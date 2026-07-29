package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.tls.TlsHelper.{toApplicationBufferSize, toPacketBufferSize}
import pl.jozwik.smtp.util.ByteBufferHelper

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status.{BUFFER_OVERFLOW, BUFFER_UNDERFLOW, CLOSED, OK}
import javax.net.ssl.{SSLEngine, SSLEngineResult, SSLException}
import scala.annotation.tailrec

trait WithSslEngine extends WithSequenceIterator with StrictLogging {

  protected def applicationBufferSize: Int

  protected def closeConnection(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Unit = {
    engine.foreach { e =>
      implicit val en: SSLEngine = e
      val a                      = Attachment.empty
      doHandshake(a)(readByteBuffer, writeByteBuffer)
      e.closeOutbound()
    }

    close()
  }

  @tailrec
  protected final def doHandshake(
      a: Attachment
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit)(implicit
      e: SSLEngine,
      seq: Int
  ): Attachment = {
    import a.*
    val b = a.buffers
    if (open.get && handshakeStatus.get != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus.get != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      val lastHandshakeStatus = handshakeStatus.get
      val engineResultHolder  = new AtomicReference[SSLEngineResult.Status]()
      logger.trace(s"$whoIAm: ($seq) doHandshake: $lastHandshakeStatus ${e.getHandshakeStatus}")
      lastHandshakeStatus match {
        case HandshakeStatus.NEED_UNWRAP =>
          val rCount = if (b.peerNetData.get().position() == 0) {
            readByteBuffer(b.peerNetData.get)
          } else {
            b.peerNetData.get().position()
          }
          if (rCount > 0) {
            b.peerNetData.get.flip()
            val peerNet = mergeWithUnderflowBuffer(b.underflowBuffer, b.peerNetData)
            val r       = unwrap(peerNet, b.peerAppDataLocal.get())
            handshakeStatus.set(r.getHandshakeStatus)
            engineResultHolder.set(r.getStatus)
            r.getStatus match {
              case OK =>
                b.peerNetData.get.compact()
                b.underflowBuffer.set(ByteBufferHelper.ReadOnlyBuffer)
              case BUFFER_OVERFLOW =>
                val buffer = ByteBufferHelper.createBuffer(b.peerAppDataLocal.get().capacity(), e.getSession.getPacketBufferSize)
                b.peerAppDataLocal.set(buffer)
              case BUFFER_UNDERFLOW =>
                handleUnderflow(b.peerNetData, b.underflowBuffer)
              case _ =>
                if (e.isOutboundDone) {
                  open.set(false)
                } else {
                  e.closeOutbound()
                  handshakeStatus.set(e.getHandshakeStatus)
                }
            }
          } else if (rCount < 0) {
            if (e.isInboundDone && e.isOutboundDone) {
              open.set(false)
            } else {
              try {
                e.closeInbound()
              } catch {
                case e: SSLException =>
                  logger.error(
                    "This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.",
                    e
                  )
              }
              e.closeOutbound()
              handshakeStatus.set(e.getHandshakeStatus)
            }
          }

        case HandshakeStatus.NEED_WRAP =>
          b.myNetData.get.clear
          val result = wrap(b.myAppDataLocal, b.myNetData.get())
          engineResultHolder.set(result.getStatus)
          handshakeStatus.set(result.getHandshakeStatus)
          result.getStatus match {
            case OK =>
              writeToChannel(Option(result))(b.myNetData.get())(writeByteBuffer)
            case BUFFER_OVERFLOW =>
              val newBuffer = ByteBufferHelper.createBuffer(b.myNetData.get().capacity(), e.getSession.getPacketBufferSize)
              b.myNetData.set(newBuffer)
            case BUFFER_UNDERFLOW =>
              throw new SSLException("Buffer underflow occurred after a wrap. I don't think we should ever get here.")
            case _ =>
              try {
                writeToChannel(Option(result))(b.myNetData.get())(writeByteBuffer)
                b.peerNetData.get.clear()
              } catch {
                case exp: Exception =>
                  logger.error("Failed to send server's CLOSE message due to socket channel's failure.", exp)
                  handshakeStatus.set(e.getHandshakeStatus)
              }
          }

        case HandshakeStatus.NEED_TASK =>
          TlsHelper.runDelegatedTasks(e)
          handshakeStatus.set(e.getHandshakeStatus)
        case _ =>

      }
      if (engineResultHolder.get() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
        a
      } else if (
        (b.peerNetData.get().position() > 0 && handshakeStatus.get == HandshakeStatus.NEED_UNWRAP) ||
        handshakeRepeatOn.contains(
          handshakeStatus.get
        ) || lastHandshakeStatus == HandshakeStatus.NEED_TASK || lastHandshakeStatus == HandshakeStatus.FINISHED
      ) {
        doHandshake(a)(readByteBuffer, writeByteBuffer)
      } else {
        a
      }
    } else {
      if (handshakeStatus.get == HandshakeStatus.FINISHED) {
        ownHandshakeFinished()
      }
      a
    }

  }

  protected def handleRead(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer]
  ): (Option[ByteBuffer], Option[SSLEngineResult])

  protected def handshakeRepeatOnExtra: Set[HandshakeStatus]

  protected def ownHandshakeFinished(): Unit

  protected def packetBufferSize: Int

  protected def peerHandshakeFinished(): Unit

  protected def read(
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val peerNetData = new AtomicReference(ByteBuffer.allocate(toPacketBufferSize(packetBufferSize)))
    val bytes       = readByteBuffer(peerNetData.get)
    logger.trace(s"$whoIAm: ($seq)  Read $bytes from a $whoContactMe, engine=${engine.map(_.getHandshakeStatus)}")
    bytes match {
      case 0 =>
        (Option(ByteBufferHelper.ReadOnlyBuffer), None)
      case bytesRead if bytesRead > 0 =>
        peerNetData.get.flip()
        readBuffer(underflowBuffer)(peerNetData, exitRead, closed)(readByteBuffer, writeByteBuffer, closeConn)
      case _ =>
        handleEndOfStream(readByteBuffer, writeByteBuffer, closeConn)
        exitRead(true)
        closed(true)
        (None, None)
    }
  }

  protected def setEngineModeAndStartHandshake(a: Attachment, useClientMode: Boolean)(implicit e: SSLEngine): Attachment = {
    e.setUseClientMode(useClientMode)
    e.beginHandshake()
    val appBufferSize    = e.getSession.getApplicationBufferSize
    val packetBufferSize = e.getSession.getPacketBufferSize
    val b                = Buffers(appBufferSize, packetBufferSize)
    a.withEngine(b)
  }

  protected def whoContactMe: String

  protected def whoIAm: String

  protected def write(
      message: ByteBuffer
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int, engine: Option[SSLEngine]): Unit = {
    val myAppData = ByteBuffer.allocate(toApplicationBufferSize(applicationBufferSize))
    val myNetData = new AtomicReference(ByteBuffer.allocate(toPacketBufferSize(packetBufferSize)))
    myAppData.put(message)
    myAppData.flip()
    writeMessage(myAppData, myNetData)(readByteBuffer, writeByteBuffer, closeConn)
  }

  private def handleUnderflow(peerNetData: AtomicReference[ByteBuffer], underflowBuffer: AtomicReference[ByteBuffer])(implicit
      seq: Int,
      e: SSLEngine
  ): ByteBuffer = {
    logger.debug(s"($seq) ${peerNetData.get}")
    underflowBuffer.accumulateAndGet(peerNetData.get, ByteBufferHelper.merge)
    peerNetData.get.position(peerNetData.get.limit())
  }

  private def handleEndOfStream(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Unit = {
    try {
      engine.foreach { e =>
        e.closeInbound()
      }
    } catch {
      case e: SSLException =>
        logger.error("", e)
    }
    closeConnection(readByteBuffer, writeByteBuffer, close)
  }

  private def handleError(implicit engine: SSLEngine, seq: Int): PartialFunction[Throwable, SSLEngineResult] = { case e: SSLException =>
    logger.error(
      s"$whoIAm: ($seq) Will try to properly close connection...",
      e
    )
    engine.closeOutbound()
    TlsHelper.failedHandshakeResult(engine.getHandshakeStatus)
  }

  private val handshakeRepeatOn = Set(HandshakeStatus.NEED_TASK, HandshakeStatus.FINISHED) ++ handshakeRepeatOnExtra

  private def readBuffer(underflowBuffer: AtomicReference[ByteBuffer])(
      peerNetData: AtomicReference[ByteBuffer],
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val peerAppData = new AtomicReference(ByteBuffer.allocate(toApplicationBufferSize(applicationBufferSize)))
    val l           = readLoop(underflowBuffer)(peerNetData, peerAppData, exitRead, closed)(readByteBuffer, writeByteBuffer, closeConn)
    l match {
      case r @ Some(s)
          if (s.getHandshakeStatus == HandshakeStatus.FINISHED || s.getStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) && s.bytesProduced() == 0 =>
        (None, r)
      case r =>
        (Option(peerAppData.get), r)
    }
  }

  @tailrec
  private def readLoop(underflowBuffer: AtomicReference[ByteBuffer])(
      peerNetData: AtomicReference[ByteBuffer],
      peerAppData: AtomicReference[ByteBuffer],
      exitRead: Boolean => Unit,
      closed: Boolean => Unit,
      result: Option[SSLEngineResult] = None
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Option[SSLEngineResult] =
    if (peerNetData.get.hasRemaining) {
      engine match {
        case Some(e) =>
          implicit val en: SSLEngine = e
          val peerNet                = mergeWithUnderflowBuffer(underflowBuffer, peerNetData)
          val r                      = unwrap(peerNet, peerAppData.get)
          r.getStatus match {
            case OK =>
              peerAppData.get.flip()
              exitRead(true)
              underflowBuffer.set(ByteBufferHelper.ReadOnlyBuffer)
            case BUFFER_OVERFLOW =>
              val buffer = ByteBufferHelper.createBuffer(peerAppData.get().capacity(), e.getSession.getApplicationBufferSize)
              peerAppData.set(buffer)
            case BUFFER_UNDERFLOW =>
              handleUnderflow(peerNetData, underflowBuffer)
            case _ =>
              closeConnection(readByteBuffer, writeByteBuffer, closeConn)
              closed(true)
          }
          readLoop(underflowBuffer)(peerNetData, peerAppData, exitRead, closed, Option(r))(readByteBuffer, writeByteBuffer, closeConn)
        case _ =>
          peerAppData.get().put(peerNetData.get)
          result
      }
    } else {
      result
    }

  private def mergeWithUnderflowBuffer(underflowBuffer: AtomicReference[ByteBuffer], peerNetData: AtomicReference[ByteBuffer]) =
    underflowBuffer.get() match {
      case b if b.remaining() == 0 =>
        peerNetData.get
      case underflow =>
        ByteBufferHelper.merge(underflow, peerNetData.get())
    }

  private def unwrap(peerNetData: ByteBuffer, peerAppDataLocal: ByteBuffer)(implicit seq: Int, engine: SSLEngine): SSLEngineResult =
    try {
      engine.unwrap(peerNetData, peerAppDataLocal)
    } catch {
      handleError
    }

  private def wrap(myAppDataLocal: ByteBuffer, myNetData: ByteBuffer)(implicit seq: Int, engine: SSLEngine): SSLEngineResult =
    try {
      engine.wrap(myAppDataLocal, myNetData)
    } catch {
      handleError
    }

  @tailrec
  private def writeMessage(
      myAppData: ByteBuffer,
      myNetData: AtomicReference[ByteBuffer]
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int, engine: Option[SSLEngine]): Unit =
    if (myAppData.hasRemaining) {
      val continue = new AtomicReference(true)
      engine match {
        case Some(e) =>
          implicit val en: SSLEngine = e
          myNetData.get.clear()
          val result = wrap(myAppData, myNetData.get)
          result.getStatus match {
            case OK =>
              writeToChannel()(myNetData.get())(writeByteBuffer)
              continue.set(false)
            case BUFFER_OVERFLOW =>
              val buffer = ByteBufferHelper.createBuffer(myNetData.get().capacity(), e.getSession.getPacketBufferSize)
              myNetData.set(buffer)
            case CLOSED =>
              closeConnection(readByteBuffer, writeByteBuffer, closeConn)
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
        writeMessage(myAppData, myNetData)(readByteBuffer, writeByteBuffer, closeConn)
      }
    }

  private def writeToChannel(status: Option[SSLEngineResult] = None)(myNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit = {
    myNetData.flip()
    writeToChannelLoop(status)(myNetData)(writeByteBuffer)
  }

  @tailrec
  private def writeToChannelLoop(status: Option[SSLEngineResult])(myNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit =
    if (myNetData.hasRemaining) {
      writeByteBuffer(myNetData)
      writeToChannelLoop(status)(myNetData)(writeByteBuffer)
    }

}
