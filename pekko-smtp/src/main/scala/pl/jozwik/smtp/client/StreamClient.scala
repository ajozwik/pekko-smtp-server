package pl.jozwik.smtp.client

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Flow, Framing, Keep, Sink, SinkQueue, SinkQueueWithCancel, Source, SourceQueue, SourceQueueWithComplete, Tcp}
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.{SmtpUtils, TlsOpts}
import pl.jozwik.smtp.util.{Constants, Mail, SmtpCodes, SocketAddress, Utils}
import Constants.*
import org.apache.pekko.stream.OverflowStrategy
import pl.jozwik.smtp.tls.TlsHelper.toApplicationBufferSize
import pl.jozwik.smtp.tls.{Attachment, BufferAction, SSLContextFactory, WithSslEngineClient}

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngine
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

class StreamClient(host: String, port: Int, tlsOpts: Option[TlsOpts] = None)(implicit system: ActorSystem) extends SenderClient with WithSslEngineClient {
  import system.dispatcher

  def this(serverAddress: SocketAddress)(implicit system: ActorSystem) =
    this(serverAddress.host, serverAddress.port)

  def this(serverAddress: SocketAddress, tlsOpts: TlsOpts)(implicit system: ActorSystem) =
    this(serverAddress.host, serverAddress.port, Option(tlsOpts))

  private val QueueSize                                               = 8
  private val timeout                                                 = 2.second
  private val dummySession                                            = javax.net.ssl.SSLContext.getDefault.createSSLEngine.getSession
  protected val (applicationBufferSize, packetBufferSize)             = (dummySession.getApplicationBufferSize, dummySession.getPacketBufferSize)
  protected override def handshakeRepeatOnExtra: Set[HandshakeStatus] = Set(HandshakeStatus.NEED_WRAP)

  private val connection: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
    Tcp().outgoingConnection(host, port)

  def sendMail(mail: Mail): Future[Result] =
    tlsOpts match {
      case Some(opts) => runStartTlsSession(mail, opts).recover(recoverError)
      case _          => sendMailPlain(mail)
    }

  private def sendMailPlain(mail: Mail): Future[Result] = {
    val source = Source.single(mail)
    val future = source
      .map { mail =>
        Seq(s"$EHLO ${mail.from.domain}", s"$MAIL_FROM: ${mail.from}") ++
          mail.to.map(to => s"$RCPT_TO:$to") ++
          Seq(s"$DATA", s"$Subject:${mail.emailContent.subject}", "", mail.emailContent.bodyAsString, END_DATA, QUIT)
      }
      .map(seq => ByteString(seq.map(Utils.withEndOfLine).mkString))
      .via(connection)
      .via(Framing.delimiter(ByteString("\n"), toApplicationBufferSize(applicationBufferSize)(None), allowTruncation = true))
      .runFold[(Result, Seq[Int])]((SuccessResult, Seq.empty[Int])) { case ((acc, codes), message) =>
        val response = SmtpUtils.toInt(message.take(3).utf8String)
        logger.trace(s"${message.utf8String}")
        val newAcc = acc match {
          case f: FailedResult =>
            f
          case _ if isResponseSuccess(response) =>
            acc
          case _ =>
            FailedResult((message.utf8String + Constants.Delimiter).stripLineEnd)
        }

        (newAcc, response.map(c => c +: codes).getOrElse(codes))
      }
    future
      .map(finalResult)
      .recover(recoverError)
  }

  private def finalResult(acc: (Result, Seq[Int])): Result =
    acc match {
      case (SuccessResult, codes) if !codes.containsSlice(Seq(SmtpCodes.REQUEST_COMPLETE, SmtpCodes.START_MAIL_INPUT)) =>
        FailedResult("")
      case (result, _) =>
        result
    }

  private def recoverError: PartialFunction[Throwable, Result] = { case e: Throwable =>
    logger.error("", e)
    FailedResult(e.getMessage)
  }

  private def isResponseSuccess(response: Option[Int]) =
    response.exists(r => r >= 200 && r < 400)

  // --- STARTTLS support -----------------------------------------------------------------------------------------

  private def clientSslEngine(opts: TlsOpts): SSLEngine = {
    val context = SSLContextFactory.createContext(opts.protocol)(
      opts.keyStoreInputStream.call(),
      opts.keystorePassword,
      opts.keyPassword
    )(opts.trustStoreInputStream.call(), opts.trustPassword)
    context.createSSLEngine(host, port)
  }

  private def closeConn(implicit sourceQueue: SourceQueueWithComplete[ByteString], sinkQueue: SinkQueueWithCancel[ByteString]): Unit = {
    sourceQueue.complete()
    sinkQueue.cancel()
  }

  private def negotiateTls(opts: TlsOpts)(implicit
      sourceQueue: SourceQueue[ByteString],
      engine: AtomicReference[Option[SSLEngine]],
      sinkQueue: SinkQueue[ByteString]
  ): Future[Boolean] = {
    implicit val e: SSLEngine = clientSslEngine(opts)
    val attachment            = setEngineModeAndStartHandshake(Attachment.empty, useClientMode = true)
    for {
      _ <- doHandshakeStep(attachment, readBlocking(sinkQueue))
      _ <-
        if (handshakeDone(attachment)) {
          Future.unit
        } else {
          runHandshake(attachment)
        }
    } yield {
      val success = attachment.open.get() &&
        (attachment.handshakeStatus.get() == HandshakeStatus.FINISHED || attachment.handshakeStatus.get() == HandshakeStatus.NOT_HANDSHAKING)
      if (success) {
        engine.set(Option(e))
      }
      success
    }
  }

  private def runStartTlsSession(mail: Mail, opts: TlsOpts): Future[Result] = {
    implicit val (sourceQueue: SourceQueueWithComplete[ByteString], sinkQueue: SinkQueueWithCancel[ByteString]) =
      Source
        .queue[ByteString](QueueSize, OverflowStrategy.backpressure)
        .viaMat(connection)(Keep.left)
        .toMat(Sink.queue[ByteString]())(Keep.both)
        .run()

    implicit val pending: AtomicReference[ByteString]       = new AtomicReference(ByteString.empty)
    implicit val engine: AtomicReference[Option[SSLEngine]] = new AtomicReference(None)
    implicit def engineImplicit: Option[SSLEngine]          = engine.get()
    implicit val acc: AtomicReference[(Result, Seq[Int])]   = new AtomicReference((SuccessResult, Seq.empty[Int]))

    val ehlo = s"$EHLO ${mail.from.domain}"

    val task = for {
      _             <- readPendingAndResponse(closeConnection)
      _             <- step(ehlo, iterator.next()).map(toCodes)
      startTlsLines <- step(STARTTLS, iterator.next()).map { l =>
        toCodes(l)
        logger.debug(s"$l")
        l
      }
      startTlsCode = startTlsLines.headOption.flatMap(l => SmtpUtils.toInt(l.take(3)))
      _ <-
        if (isResponseSuccess(startTlsCode)) {
          for {
            n <- negotiateTls(opts)
            _ <-
              if (n) {
                step(ehlo, iterator.next()).map(toCodes)
              } else {
                Future.successful(fail("TLS handshake failed"))
              }
          } yield ()

        } else {
          Future.successful(fail(startTlsLines.headOption.getOrElse("STARTTLS not supported")))
        }

      _ <- step(s"$MAIL_FROM: ${mail.from}", iterator.next()).map(toCodes)
      _ <- Future.sequence(mail.to.map(to => step(s"$RCPT_TO:$to", iterator.next()).map(toCodes)))
      _ <- step(DATA, iterator.next()).map(toCodes)
      _ <- writeText(
        Seq(s"$Subject:${mail.emailContent.subject}", "", mail.emailContent.bodyAsString, END_DATA).map(Utils.withEndOfLine).mkString,
        closeConnection
      )
      _ <- readPendingAndResponse(closeConnection)
      _ <- step(QUIT, iterator.next()).map(toCodes)

    } yield {
      finishConnection(closeConnection)
      finalResult(acc.get())
    }
    task.recover { case e =>
      logger.error("", e)
      finishConnection(closeConnection)
      FailedResult(e.getMessage)
    }
  }

  private def closeConnection(implicit sourceQueue: SourceQueueWithComplete[ByteString], sinkQueue: SinkQueueWithCancel[ByteString]) = () => closeConn

  private def step(line: String, seq: Int)(implicit
      pending: AtomicReference[ByteString],
      engine: AtomicReference[Option[SSLEngine]],
      sourceQueue: SourceQueueWithComplete[ByteString],
      sinkQueue: SinkQueueWithCancel[ByteString]
  ): Future[Seq[String]] = {
    implicit val en: Option[SSLEngine] = engine.get()
    implicit val s: Int                = seq
    for {
      _ <- writeLine(line, closeConnection)
      r <- readResponse()(closeConnection)
    } yield {
      r
    }
  }

  private def finishConnection(closeConn: () => Unit)(implicit
      engine: Option[SSLEngine],
      sinkQueue: SinkQueue[ByteString],
      sourceQueue: SourceQueue[ByteString]
  ): Unit = {
    implicit val seq: Int = iterator.next() - 1
    val p                 = Promise[Unit]()
    closeConnection(readBlocking(sinkQueue), writeToSource(p), () => closeConn())
  }

  private def readPendingAndResponse(
      closeConn: () => Unit
  )(implicit
      pending: AtomicReference[ByteString],
      acc: AtomicReference[(Result, Seq[Int])],
      engine: AtomicReference[Option[SSLEngine]],
      sinkQueue: SinkQueue[ByteString],
      sourceQueue: SourceQueue[ByteString]
  ) = {

    implicit val seq: Int = iterator.next()
    readResponse()(() => closeConn()).map(toCodes)

  }

  private def fail(message: String)(implicit acc: AtomicReference[(Result, Seq[Int])]): Unit = {
    val (_, seq) = acc.get()
    acc.set((FailedResult(message), seq))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def readLine(pending: AtomicReference[ByteString], closeConn: () => Unit)(implicit
      engine: AtomicReference[Option[SSLEngine]],
      sourceQueue: SourceQueue[ByteString],
      sinkQueue: SinkQueue[ByteString],
      seq: Int
  ): Future[Option[String]] = {
    val idx = pending.get().indexOf('\n'.toByte)
    if (idx >= 0) {
      val line = pending.get().take(idx)
      pending.set(pending.get().drop(idx + 1))
      val stringLine = line.utf8String.stripLineEnd
      Future.successful(Option(stringLine))
    } else {

      for {
        optBytes <- readByteBufferFuture(sinkQueue)
        f        <- optBytes match {
          case Some(bytes) =>
            implicit val en: Option[SSLEngine] = engine.get()
            val p                              = Promise[Unit]()
            handleRead(BufferAction.copyTo(bytes), buff => sourceQueue.offer(ByteString(buff)).onComplete { _ => p.trySuccess(()) }, () => closeConn()) match {
              case (Some(buf), _) =>
                pending.set(pending.get() ++ extractBytes(buf))
                readLine(pending, () => closeConn())
              case (None, _) =>
                Future.successful(None)
            }
          case _ =>
            Future.successful(None)
        }
      } yield {
        f
      }
    }
  }

  private def extractBytes(buf: ByteBuffer): ByteString =
    ByteString(buf.array().takeWhile(_ != 0))

  private def handshakeDone(attachment: Attachment): Boolean =
    !attachment.open.get() ||
      attachment.handshakeStatus.get() == HandshakeStatus.FINISHED ||
      attachment.handshakeStatus.get() == HandshakeStatus.NOT_HANDSHAKING

  private def doHandshakeStep(attachment: Attachment, readByteBuffer: ByteBuffer => Int)(implicit
      e: SSLEngine,
      sourceQueue: SourceQueue[ByteString]
  ): Future[Unit] = {
    implicit val seq: Int = iterator.next()
    val p                 = Promise[Unit]()
    doHandshake(attachment)(
      readByteBuffer,
      writeToSource(p)
    )
    p.future
  }

  private def writeToSource(p: Promise[Unit])(buff: ByteBuffer)(implicit sourceQueue: SourceQueue[ByteString]): Unit =
    sourceQueue.offer(ByteString(buff)).onComplete { _ =>
      p.trySuccess(())
    }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def runHandshake(
      attachment: Attachment
  )(implicit e: SSLEngine, sinkQueue: SinkQueue[ByteString], sourceQueue: SourceQueue[ByteString]): Future[Unit] =
    for {
      b <- readByteBufferFuture(sinkQueue)
      _ <- b match {
        case Some(bytes) =>
          for {
            _ <- doHandshakeStep(attachment, BufferAction.copyTo(bytes))
            _ <-
              if (handshakeDone(attachment)) {
                Future.unit
              } else {
                runHandshake(attachment)
              }
          } yield ()
        case _ =>
          Future.failed(new IllegalStateException())

      }
    } yield ()

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def readResponse(acc: Seq[String] = Seq.empty)(closeConn: () => Unit)(implicit
      pending: AtomicReference[ByteString],
      engine: AtomicReference[Option[SSLEngine]],
      sourceQueue: SourceQueue[ByteString],
      sinkQueue: SinkQueue[ByteString],
      seq: Int
  ): Future[Seq[String]] =
    for {
      opt <- readLine(pending, () => closeConn())
      r   <- opt match {
        case Some(line) if line.length > 3 && line.charAt(3) == '-' => readResponse(acc :+ line)(closeConn)
        case Some(line)                                             => Future.successful(acc :+ line)
        case None                                                   => Future.successful(acc)
      }
    } yield {
      r
    }

  private def readByteBufferFuture[T](sinkQueue: SinkQueue[T]): Future[Option[T]] =
    sinkQueue.pull()

  private def toCodes(lines: Seq[String])(implicit acc: AtomicReference[(Result, Seq[Int])]): Unit = {
    val r = lines.foldLeft(acc.get()) { case ((result, codes), line) =>
      val response  = SmtpUtils.toInt(line.take(3))
      val newResult = result match {
        case f: FailedResult =>
          f
        case _ if isResponseSuccess(response) =>
          result
        case _ =>
          FailedResult((line + Constants.Delimiter).stripLineEnd)
      }
      (newResult, response.map(c => c +: codes).getOrElse(codes))
    }
    acc.set(r)
  }

  private def writeText(text: String, closeConn: () => Unit)(implicit
      sinkQueue: SinkQueue[ByteString],
      sourceQueue: SourceQueue[ByteString],
      engine: Option[SSLEngine]
  ): Future[Unit] =
    for {
      _ <- Future.sequence {
        text.getBytes(Constants.Utf8sCharset).grouped(toApplicationBufferSize(applicationBufferSize)).map { chunk =>
          implicit val seq: Int = iterator.next()
          val p                 = Promise[Unit]()
          write(ByteBuffer.wrap(chunk))(
            readBlocking(sinkQueue),
            writeToSource(p),
            () => closeConn()
          )
          p.future
        }
      }
    } yield ()

  private def readBlocking(sinkQueue: SinkQueue[ByteString])(buff: ByteBuffer) = {
    val bytes = Await.result(readByteBufferFuture(sinkQueue), timeout).getOrElse(ByteString.empty)
    BufferAction.copyTo(bytes)(buff)
  }

  private def writeLine(line: String, closeConn: () => Unit)(implicit
      sinkQueue: SinkQueue[ByteString],
      sourceQueue: SourceQueue[ByteString],
      engine: Option[SSLEngine]
  ): Future[Unit] =
    writeText(Utils.withEndOfLine(line), closeConn)

}
