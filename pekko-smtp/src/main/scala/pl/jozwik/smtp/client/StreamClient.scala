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
import scala.concurrent.{Future, Promise}

class StreamClient(host: String, port: Int, tlsOpts: Option[TlsOpts] = None)(implicit system: ActorSystem) extends SenderClient with WithSslEngineClient {
  import system.dispatcher

  def this(serverAddress: SocketAddress)(implicit system: ActorSystem) =
    this(serverAddress.host, serverAddress.port)

  def this(serverAddress: SocketAddress, tlsOpts: TlsOpts)(implicit system: ActorSystem) =
    this(serverAddress.host, serverAddress.port, Option(tlsOpts))

  private val QueueSize = 8

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
      .via(Framing.delimiter(ByteString("\n"), toApplicationBufferSize(None, applicationBufferSize), allowTruncation = true))
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

  private def negotiateTls(opts: TlsOpts)(implicit
      queue: SourceQueue[ByteString],
      engine: AtomicReference[Option[SSLEngine]],
      sinkQueue: SinkQueue[ByteString]
  ): Future[Boolean] = {
    implicit val e: SSLEngine = clientSslEngine(opts)
    val attachment            = setEngineModeAndStartHandshake(Attachment.empty, useClientMode = true)
    for {
      // Client always starts in NEED_WRAP: send the ClientHello before waiting for any server bytes,
      // otherwise both sides block on a read and the handshake deadlocks until the socket times out.
      _ <- doHandshakeStep(attachment, _ => -1)
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
    implicit val (queue: SourceQueueWithComplete[ByteString], sinkQueue: SinkQueueWithCancel[ByteString]) =
      Source
        .queue[ByteString](QueueSize, OverflowStrategy.backpressure)
        .viaMat(connection)(Keep.left)
        .toMat(Sink.queue[ByteString]())(Keep.both)
        .run()

    val pending: AtomicReference[ByteString]                = new AtomicReference(ByteString.empty)
    implicit val engine: AtomicReference[Option[SSLEngine]] = new AtomicReference(None)
    implicit def engineImplicit: Option[SSLEngine]          = engine.get()
    def closeConn(): Unit                                   = {
      queue.complete()
      sinkQueue.cancel()
    }

    def step(line: String): Future[Seq[String]] = for {
      _ <- writeLine(line, () => closeConn())
      r <- readResponse()(pending, () => closeConn())
    } yield {
      r
    }

    val ehlo                                              = s"$EHLO ${mail.from.domain}"
    implicit val acc: AtomicReference[(Result, Seq[Int])] = new AtomicReference((SuccessResult, Seq.empty[Int]))

    val task = for {
      _             <- readResponse()(pending, () => closeConn()).map(toCodes)
      _             <- step(ehlo).map(toCodes)
      startTlsLines <- step(STARTTLS).map { l =>
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
                step(ehlo).map(toCodes)
              } else {
                Future.successful(fail("TLS handshake failed"))
              }
          } yield ()

        } else {
          Future.successful(fail(startTlsLines.headOption.getOrElse("STARTTLS not supported")))
        }

      _ <- step(s"$MAIL_FROM: ${mail.from}").map(toCodes)
      _ <- Future.sequence(mail.to.map(to => step(s"$RCPT_TO:$to").map(toCodes)))
      _ <- step(DATA).map(toCodes)
      _ <- writeText(
        Seq(s"$Subject:${mail.emailContent.subject}", "", mail.emailContent.bodyAsString, END_DATA).map(Utils.withEndOfLine).mkString,
        () => closeConn()
      )
      _ <- readResponse()(pending, () => closeConn()).map(toCodes)
      _ <- step(QUIT).map(toCodes)

    } yield {
      closeConnection(engine.get())(() => closeConn())
      finalResult(acc.get())
    }
    task.recover { case e =>
      logger.error("", e)
      closeConnection(engine.get())(() => closeConn())
      FailedResult(e.getMessage)
    }
  }

  private def fail(message: String)(implicit acc: AtomicReference[(Result, Seq[Int])]): Unit = {
    val (_, seq) = acc.get()
    acc.set((FailedResult(message), seq))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def readLine(pending: AtomicReference[ByteString], closeConn: () => Unit)(implicit
      engine: AtomicReference[Option[SSLEngine]],
      sinkQueue: SinkQueue[ByteString]
  ): Future[Option[String]] = {
    val idx = pending.get().indexOf('\n'.toByte)
    if (idx >= 0) {
      val line = pending.get().take(idx)
      pending.set(pending.get().drop(idx + 1))
      val stringLine = line.utf8String.stripLineEnd
      Future.successful(Option(stringLine))
    } else {
      implicit val seq: Int = iterator.next()
      for {
        optBytes <- readByteBuffer(sinkQueue)
        f        <- optBytes match {
          case Some(bytes) =>
            handleRead(engine.get())(BufferAction.copyTo(bytes), () => closeConn()) match {
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
      queue: SourceQueue[ByteString]
  ): Future[Unit] = {
    implicit val seq: Int = iterator.next()
    val p                 = Promise[Unit]()
    doHandshake(attachment.buffers, attachment.handshakeStatus, attachment.open)(
      readByteBuffer,
      buff => p.trySuccess(queue.offer(ByteString(buff)))
    )
    p.future
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def runHandshake(attachment: Attachment)(implicit e: SSLEngine, sinkQueue: SinkQueue[ByteString], queue: SourceQueue[ByteString]): Future[Unit] =
    for {
      b <- readByteBuffer(sinkQueue)
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
  private def readResponse(acc: Seq[String] = Seq.empty)(pending: AtomicReference[ByteString], closeConn: () => Unit)(implicit
      engine: AtomicReference[Option[SSLEngine]],
      sinkQueue: SinkQueue[ByteString]
  ): Future[Seq[String]] =
    for {
      opt <- readLine(pending, () => closeConn())
      r   <- opt match {
        case Some(line) if line.length > 3 && line.charAt(3) == '-' => readResponse(acc :+ line)(pending, closeConn)
        case Some(line)                                             => Future.successful(acc :+ line)
        case None                                                   => Future.successful(acc)
      }
    } yield {
      r
    }

  private def readByteBuffer[T](sinkQueue: SinkQueue[T]): Future[Option[T]] =
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

  private def writeText(text: String, closeConn: () => Unit)(implicit queue: SourceQueue[ByteString], engine: Option[SSLEngine]): Future[Unit] =
    for {
      _ <- Future.sequence {
        text.getBytes(Constants.Utf8sCharset).grouped(toApplicationBufferSize(engine, applicationBufferSize)).map { chunk =>
          implicit val seq: Int = iterator.next()
          val p                 = Promise[Unit]()
          write(engine, ByteBuffer.wrap(chunk))(buff => p.trySuccess(queue.offer(ByteString(buff))), () => closeConn())
          p.future
        }
      }
    } yield ()

  private def writeLine(line: String, closeConn: () => Unit)(implicit queue: SourceQueue[ByteString], engine: Option[SSLEngine]): Future[Unit] =
    writeText(Utils.withEndOfLine(line), closeConn)

}
