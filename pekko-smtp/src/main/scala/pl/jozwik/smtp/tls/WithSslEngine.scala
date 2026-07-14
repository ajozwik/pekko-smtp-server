package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.tls.TlsHelper.{ toApplicationBufferSize, toPacketBufferSize }
import pl.jozwik.smtp.util.ByteBufferHelper

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status.{ BUFFER_OVERFLOW, BUFFER_UNDERFLOW, CLOSED, OK }
import javax.net.ssl.{ SSLEngine, SSLEngineResult, SSLException }
import scala.annotation.tailrec

trait WithSslEngine extends WithSequenceIterator with StrictLogging {

  protected def whoIAm: String
  protected def whoContactMe: String
  protected def applicationBufferSize: Int
  protected def packetBufferSize: Int
  protected def handshakeRepeatOnExtra: Set[HandshakeStatus]

  private val handshakeRepeatOn = Set(HandshakeStatus.NEED_TASK, HandshakeStatus.FINISHED) ++ handshakeRepeatOnExtra

  protected def handleRead(
      engine: Option[SSLEngine]
  )(readByteBuffer: ByteBuffer => Int, closeConn: () => Unit)(implicit seq: Int): (Option[ByteBuffer], Option[SSLEngineResult])

  protected def setEngineModeAndStartHandshake(a: Attachment, e: SSLEngine, useClientMode: Boolean): Attachment = {
    e.setUseClientMode(useClientMode)
    e.beginHandshake()
    val appBufferSize    = e.getSession.getApplicationBufferSize
    val packetBufferSize = e.getSession.getPacketBufferSize
    val b                = Buffers(appBufferSize, packetBufferSize)
    a.withEngine(e, b)
  }

  protected def read(
      engine: Option[SSLEngine],
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(readByteBuffer: ByteBuffer => Int, closeConn: () => Unit)(implicit seq: Int): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val peerNetData = new AtomicReference(ByteBuffer.allocate(toPacketBufferSize(engine, packetBufferSize)))
    val bytes       = readByteBuffer(peerNetData.get)
    logger.trace(s"$whoIAm: ($seq)  Read $bytes from a $whoContactMe, engine=${engine.map(_.getHandshakeStatus)}")
    bytes match {
      case 0 =>
        (Option(ByteBufferHelper.ReadOnlyBuffer), None)
      case bytesRead if bytesRead > 0 =>
        peerNetData.get.flip()
        readBuffer(engine, peerNetData, exitRead, closed)(closeConn)
      case x =>
        logger.trace(s"$whoIAm: ($seq)  Received end of stream. Close connection with $whoContactMe $x")
        handleEndOfStream(engine)(closeConn)
        exitRead(true)
        closed(true)
        logger.trace(s"$whoIAm: ($seq)  Goodbye $whoContactMe!")
        (None, None)
    }
  }

  private def readBuffer(
      engine: Option[SSLEngine],
      peerNetData: AtomicReference[ByteBuffer],
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(closeConn: () => Unit)(implicit seq: Int): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val peerAppData = new AtomicReference(ByteBuffer.allocate(toApplicationBufferSize(engine, applicationBufferSize)))
    readLoop(engine, peerNetData, peerAppData, exitRead, closed)(closeConn) match {
      case r @ Some(s) if s.getHandshakeStatus == HandshakeStatus.FINISHED && s.bytesProduced() == 0 =>
        (None, r)
      case r =>
        (Option(peerAppData.get), r)
    }
  }

  @tailrec
  private def readLoop(
      engine: Option[SSLEngine],
      peerNetData: AtomicReference[ByteBuffer],
      peerAppData: AtomicReference[ByteBuffer],
      exitRead: Boolean => Unit,
      closed: Boolean => Unit,
      result: Option[SSLEngineResult] = None
  )(closeConn: () => Unit)(implicit seq: Int): Option[SSLEngineResult] =
    if (peerNetData.get.hasRemaining) {
      engine match {
        case Some(e) =>
          val r = unwrap(peerNetData.get, peerAppData.get, e)
          logger.trace(s"$whoIAm: ($seq) $peerNetData '${ByteBufferHelper.toString(peerAppData.get)}' $r")
          r.getStatus match {
            case OK =>
              peerAppData.get.flip()
              exitRead(true)
            case BUFFER_OVERFLOW =>
              val buffer = ByteBufferHelper.createBuffer(peerAppData.get().capacity(), e.getSession.getApplicationBufferSize)
              peerAppData.set(buffer)
            case BUFFER_UNDERFLOW =>
              ByteBufferHelper.handleBufferUnderflow(e.getSession.getPacketBufferSize, peerNetData)
            case _ =>
              logger.trace(s"$whoIAm: ($seq) $whoContactMe wants to close connection...")
              closeConnection(engine)(closeConn)
              closed(true)

          }
          readLoop(engine, peerNetData, peerAppData, exitRead, closed, Option(r))(closeConn)
        case _ =>
          peerAppData.get().put(peerNetData.get)
          result
      }
    } else {
      result
    }

  @tailrec
  protected final def doHandshake(
      engine: SSLEngine,
      b: Buffers,
      handshakeStatus: AtomicReference[HandshakeStatus],
      open: AtomicBoolean
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit = {
    import b.*
    if (open.get && handshakeStatus.get != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus.get != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      val lastHandshakeStatus = handshakeStatus.get
      logger.trace(s"$whoIAm: ($seq) doHandshake: $lastHandshakeStatus ${engine.getHandshakeStatus}")
      lastHandshakeStatus match {
        case HandshakeStatus.NEED_UNWRAP =>
          val rCount = if (peerNetData.get().position() == 0) {
            readByteBuffer(peerNetData.get)
          } else {
            peerNetData.get().position()
          }
          logger.trace(s"$whoIAm: ($seq) ${handshakeStatus.get()} $rCount ${peerNetData.get()}")
          if (rCount > 0) {
            peerNetData.get.flip()
            val engineResult = unwrap(peerNetData.get(), peerAppDataLocal.get(), engine)
            if (engineResult.getHandshakeStatus == HandshakeStatus.FINISHED) {
              logger.trace(s"$whoIAm: ($seq) $peerNetData")
            }
            peerNetData.get.compact()
            if (engineResult.getHandshakeStatus == HandshakeStatus.FINISHED) {
              logger.trace(s"$whoIAm: ($seq) $peerNetData")
            }
            handshakeStatus.set(engineResult.getHandshakeStatus)
            engineResult.getStatus match {
              case OK              =>
              case BUFFER_OVERFLOW =>
                val buffer = ByteBufferHelper.createBuffer(peerAppDataLocal.get().capacity(), engine.getSession.getPacketBufferSize)
                peerAppDataLocal.set(buffer)
              case BUFFER_UNDERFLOW =>
                ByteBufferHelper.handleBufferUnderflow(engine.getSession.getPacketBufferSize, peerNetData)
              case _ =>
                if (engine.isOutboundDone) {
                  open.set(false)
                } else {
                  engine.closeOutbound()
                  handshakeStatus.set(engine.getHandshakeStatus)
                }
            }
          } else if (rCount < 0) {
            if (engine.isInboundDone && engine.isOutboundDone) {
              open.set(false)
            } else {
              try {
                engine.closeInbound()
              } catch {
                case e: SSLException =>
                  logger.error(
                    "This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.",
                    e
                  )
              }
              engine.closeOutbound()
              handshakeStatus.set(engine.getHandshakeStatus)
            }
          }

        case HandshakeStatus.NEED_WRAP =>
          myNetData.get.clear
          val result = wrap(myAppDataLocal, myNetData.get(), engine)

          handshakeStatus.set(result.getHandshakeStatus)
          result.getStatus match {
            case OK =>
              logger.trace(s"$whoIAm: ($seq) $result $myAppDataLocal")
              writeToChannel(Option(result))(myNetData.get())(writeByteBuffer)
            case BUFFER_OVERFLOW =>
              val newBuffer = ByteBufferHelper.createBuffer(myNetData.get().capacity(), engine.getSession.getPacketBufferSize)
              myNetData.set(newBuffer)
            case BUFFER_UNDERFLOW =>
              throw new SSLException("Buffer underflow occurred after a wrap. I don't think we should ever get here.")
            case _ =>
              try {
                writeToChannel(Option(result))(myNetData.get())(writeByteBuffer)
                peerNetData.get.clear()
              } catch {
                case e: Exception =>
                  logger.error("Failed to send server's CLOSE message due to socket channel's failure.", e)
                  handshakeStatus.set(engine.getHandshakeStatus)
              }
          }

        case HandshakeStatus.NEED_TASK =>
          TlsHelper.runDelegatedTasks(engine)
          handshakeStatus.set(engine.getHandshakeStatus)
        case _ =>

      }
      if (
        (peerNetData.get().position() > 0 && handshakeStatus.get == HandshakeStatus.NEED_UNWRAP) ||
        handshakeRepeatOn.contains(handshakeStatus.get) || lastHandshakeStatus == HandshakeStatus.NEED_TASK || lastHandshakeStatus == HandshakeStatus.FINISHED
      ) {
        doHandshake(engine, b, handshakeStatus, open)(readByteBuffer, writeByteBuffer)
      }
    } else {
      logger.trace(s"$whoIAm: ($seq) Finished doHandshake: ${engine.getHandshakeStatus} handshakeStatus=$handshakeStatus")
      if (handshakeStatus.get == HandshakeStatus.FINISHED) {
        ownHandshakeFinished()
      }
    }

  }

  private def wrap(myAppDataLocal: ByteBuffer, myNetData: ByteBuffer, engine: SSLEngine)(implicit seq: Int) =
    try {
      val r = engine.wrap(myAppDataLocal, myNetData)
      logger.trace(s"$whoIAm: wrap ($seq) $r")
      r
    } catch {
      handleError(engine)
    }

  private def unwrap(peerNetData: ByteBuffer, peerAppDataLocal: ByteBuffer, engine: SSLEngine)(implicit
      seq: Int
  ): SSLEngineResult =
    try {
      val r = engine.unwrap(peerNetData, peerAppDataLocal)
      logger.trace(s"$whoIAm: unwrap ($seq) $r ${engine.getHandshakeStatus} $peerNetData")
      r
    } catch {
      handleError(engine)
    }

  protected def ownHandshakeFinished(): Unit

  protected def peerHandshakeFinished(): Unit

  private def handleError(engine: SSLEngine): PartialFunction[Throwable, SSLEngineResult] = { case e: SSLException =>
    logger.error(
      s"$whoIAm: Will try to properly close connection...",
      e
    )
    engine.closeOutbound()
    TlsHelper.failedHandshakeResult(engine.getHandshakeStatus)
  }

  private def writeToChannel(status: Option[SSLEngineResult] = None)(myNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit = {
    myNetData.flip()
    writeToChannelLoop(status)(myNetData)(writeByteBuffer)
  }

  @tailrec
  private def writeToChannelLoop(status: Option[SSLEngineResult])(myNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit =
    if (myNetData.hasRemaining) {
      if (status.isDefined) {
        logger.trace(s"$whoIAm: ($seq)  writeToChannelLoop  $status $myNetData")
      }
      writeByteBuffer(myNetData)
      writeToChannelLoop(status)(myNetData)(writeByteBuffer)
    }

  protected def write(
      engine: Option[SSLEngine],
      message: ByteBuffer
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int): Unit = {
    logger.trace(s"$whoIAm: ($seq)  writes to a $whoContactMe: ${ByteBufferHelper.toString(message).trim}")
    val myAppData = ByteBuffer.allocate(toApplicationBufferSize(engine, applicationBufferSize))
    val myNetData = new AtomicReference(ByteBuffer.allocate(toPacketBufferSize(engine, packetBufferSize)))
    myAppData.put(message)
    myAppData.flip()
    writeMessage(engine, myAppData, myNetData)(writeByteBuffer, closeConn)
  }

  @tailrec
  private def writeMessage(
      engine: Option[SSLEngine],
      myAppData: ByteBuffer,
      myNetData: AtomicReference[ByteBuffer]
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int): Unit =
    if (myAppData.hasRemaining) {
      val continue = new AtomicReference(true)
      engine match {
        case Some(e) =>
          myNetData.get.clear()
          val result = wrap(myAppData, myNetData.get, e)
          result.getStatus match {
            case OK =>
              writeToChannel()(myNetData.get())(writeByteBuffer)
              val message = ByteBufferHelper.toString(myAppData)
              logger.trace(s"$whoIAm: ($seq)  Message sent to the $whoContactMe: $message")
              continue.set(false)
            case BUFFER_OVERFLOW =>
              val buffer = ByteBufferHelper.createBuffer(myNetData.get().capacity(), e.getSession.getPacketBufferSize)
              myNetData.set(buffer)
            case CLOSED =>
              closeConnection(engine)(closeConn)
              continue.set(false)
            case s =>
              throw new SSLException(s"Buffer underflow occurred after a wrap. I don't think we should ever get here. $s")
          }
        case _ =>
          myNetData.get.put(myAppData)
          myNetData.get.flip()
          writeToChannelLoop(None)(myNetData.get())(writeByteBuffer)
      }
      if (continue.get) {
        writeMessage(engine, myAppData, myNetData)(writeByteBuffer, closeConn)
      }
    }

  protected def closeConnection(
      engine: Option[SSLEngine]
  )(close: () => Unit): Unit = {
    engine.foreach { _.closeOutbound() }
    close()
  }

  private def handleEndOfStream(engine: Option[SSLEngine])(close: () => Unit): Unit = {
    try {
      engine.foreach { e =>
        e.closeInbound()
      }
    } catch {
      case e: SSLException =>
        logger.warn(e.getMessage)
    }
    closeConnection(engine)(close)
  }

}
