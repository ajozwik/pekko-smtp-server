package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.tls.TlsHelper.{toApplicationBufferSize, toPacketBufferSize}
import pl.jozwik.smtp.util.{ByteBufferHelper, Utils}

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
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
      doHandshake(a)(readByteBuffer, writeByteBuffer, close)
      e.closeOutbound()
    }

    close()
  }

  @tailrec
  protected final def doHandshake(
      a: Attachment,
      engineResultHolder: AtomicReference[SSLEngineResult] =
        new AtomicReference[SSLEngineResult](TlsHelper.failedHandshakeResult(HandshakeStatus.NOT_HANDSHAKING))
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      e: SSLEngine,
      seq: Int
  ): Attachment = {
    import a.*
    val b = a.buffers
    if (open.get && handshakeStatus.get != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus.get != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
      val lastHandshakeStatus = handshakeStatus.get

      logger.trace(s"$whoIAm: ($seq) doHandshake: $lastHandshakeStatus ${e.getHandshakeStatus}")
      lastHandshakeStatus match {
        case HandshakeStatus.NEED_UNWRAP =>
          val bytesCount = if (b.peerNetData.get().position() == 0 && engineResultHolder.get().getStatus != SSLEngineResult.Status.BUFFER_OVERFLOW) {
            readByteBuffer(b.peerNetData.get)
          } else {
            b.peerNetData.get().position()
          }
          if (bytesCount > 0) {
            b.peerNetData.get.flip()
            val peerNet = mergeWithUnderflowBuffer(b.underflowBuffer, b.peerNetData.get())
            val r       = unwrap(peerNet, b.peerAppDataLocal.get())
            handshakeStatus.set(r.getHandshakeStatus)
            engineResultHolder.set(r)
            r.getStatus match {
              case OK =>
                val extraBytes = (peerNet ne b.peerNetData.get()) && peerNet.hasRemaining
                b.peerNetData.get.compact()
                b.underflowBuffer.set(if (extraBytes) ByteBufferHelper.clone(peerNet) else ByteBufferHelper.ReadOnlyBuffer)
              case BUFFER_UNDERFLOW =>
                handleUnderflow(peerNet, b.underflowBuffer)
                ByteBufferHelper.clearBuffer(b.peerNetData.get())
              case _ =>
                if (e.isOutboundDone) {
                  open.set(false)
                } else {
                  e.closeOutbound()
                  handshakeStatus.set(e.getHandshakeStatus)
                }
            }
          } else if (bytesCount < 0) {
            if (e.isInboundDone && e.isOutboundDone) {
              open.set(false)
            } else {
              Utils.ignoreErrors(e.closeInbound(), e.closeOutbound())
              handshakeStatus.set(e.getHandshakeStatus)
            }
          }

        case HandshakeStatus.NEED_WRAP =>
          b.myNetData.get.clear
          val result = wrap(b.myAppDataLocal, b.myNetData.get())
          engineResultHolder.set(result)
          handshakeStatus.set(result.getHandshakeStatus)
          result.getStatus match {
            case OK =>
              writeToChannel(Option(result))(b.myNetData.get())(writeByteBuffer)
            case BUFFER_OVERFLOW =>
              val newBuffer = ByteBufferHelper.createBuffer(b.myNetData.get().capacity(), e.getSession.getPacketBufferSize)
              b.myNetData.set(newBuffer)
            case _ =>
              handshakeStatus.set(e.getHandshakeStatus)
              implicit val en: Option[SSLEngine] = Option(e)
              handleEndOfStream(readByteBuffer, writeByteBuffer, close)
          }

        case HandshakeStatus.NEED_TASK =>
          TlsHelper.runDelegatedTasks(e)
          handshakeStatus.set(e.getHandshakeStatus)
          b.underflowBuffer.set(ByteBufferHelper.ReadOnlyBuffer)
        case _ =>

      }
      if (engineResultHolder.get().getStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
        a
      } else if (
        (b.peerNetData.get().position() > 0 && handshakeStatus.get == HandshakeStatus.NEED_UNWRAP) ||
        handshakeRepeatOn.contains(
          handshakeStatus.get
        ) || lastHandshakeStatus == HandshakeStatus.NEED_TASK || lastHandshakeStatus == HandshakeStatus.FINISHED ||
        engineResultHolder.get().getStatus == SSLEngineResult.Status.BUFFER_OVERFLOW
      ) {
        doHandshake(a, engineResultHolder)(readByteBuffer, writeByteBuffer, close)
      } else {
        a
      }
    } else {
      if (handshakeStatus.get == HandshakeStatus.FINISHED) {
        ownHandshakeFinished(b.peerNetData.get().flip())
      }
      a
    }

  }

  protected def handleRead(peerNetData: ByteBuffer)(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): (Option[ByteBuffer], Option[SSLEngineResult])

  protected def handshakeRepeatOnExtra: Set[HandshakeStatus]

  protected def ownHandshakeFinished(remaining: ByteBuffer): Unit

  protected def packetBufferSize: Int

  protected def peerHandshakeFinished(remaining: ByteBuffer): Unit

  protected def read(peerNetData: ByteBuffer)(
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val bytes = peerNetData.remaining()
    logger.trace(s"$whoIAm: Read ($seq) $bytes from a $whoContactMe, engine=${engine.map(_.getHandshakeStatus)}")
    bytes match {
      case 0 =>
        (Option(ByteBufferHelper.ReadOnlyBuffer), None)
      case bytesRead if bytesRead > 0 =>
        readBuffer(underflowBuffer)(peerNetData, open, exitRead, closed)(readByteBuffer, writeByteBuffer, closeConn)
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
    val b = createBuffers(e)
    a.withEngine(b)
  }

  // For test purpose
  protected def createBuffers(implicit e: SSLEngine): Buffers = {
    val appBufferSize    = e.getSession.getApplicationBufferSize
    val packetBufferSize = e.getSession.getPacketBufferSize
    Buffers(appBufferSize, packetBufferSize)
  }

  protected def createApplicationBuffer(implicit e: Option[SSLEngine]): ByteBuffer =
    ByteBuffer.allocate(toApplicationBufferSize(applicationBufferSize))

  protected def createPacketBuffer(implicit e: Option[SSLEngine]): ByteBuffer =
    ByteBuffer.allocate(toPacketBufferSize(packetBufferSize))

  protected def whoContactMe: String

  protected def whoIAm: String

  protected def write(
      message: ByteBuffer
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int, engine: Option[SSLEngine]): Unit = {
    val myAppData = ByteBufferHelper.clone(message)
    val myNetData = new AtomicReference(createPacketBuffer)
    writeMessage(myAppData, myNetData)(readByteBuffer, writeByteBuffer, closeConn)
  }

  private def handleUnderflow(peerNetData: ByteBuffer, underflowBuffer: AtomicReference[ByteBuffer])(implicit
      seq: Int
  ): ByteBuffer = {
    logger.trace(s"$whoIAm handleUnderflow ($seq) $peerNetData")
    underflowBuffer.accumulateAndGet(peerNetData, ByteBufferHelper.mergeAndFlip)
  }

  private def handleEndOfStream(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Unit = {
    Utils.ignoreError {
      engine.foreach { e =>
        e.closeInbound()
      }
    }
    closeConnection(readByteBuffer, writeByteBuffer, close)
  }

  private def handleError(implicit engine: SSLEngine, seq: Int): PartialFunction[Throwable, SSLEngineResult] = { case e: SSLException =>
    logger.error(
      s"$whoIAm: ($seq) Will try to properly close connection...",
      e
    )
    engine.closeOutbound()
    throw e
  }

  private val handshakeRepeatOn = Set(HandshakeStatus.NEED_TASK, HandshakeStatus.FINISHED) ++ handshakeRepeatOnExtra

  private def readBuffer(underflowBuffer: AtomicReference[ByteBuffer])(
      peerNetData: ByteBuffer,
      open: AtomicBoolean,
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val peerAppData = new AtomicReference(createApplicationBuffer)
    val l           = readLoop(underflowBuffer)(peerNetData, peerAppData, open, exitRead, closed)(readByteBuffer, writeByteBuffer, closeConn)
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
      peerNetData: ByteBuffer,
      peerAppData: AtomicReference[ByteBuffer],
      open: AtomicBoolean,
      exitRead: Boolean => Unit,
      closed: Boolean => Unit,
      engineResultHolder: AtomicReference[SSLEngineResult] = new AtomicReference(TlsHelper.failedHandshakeResult(HandshakeStatus.NOT_HANDSHAKING)),
      result: Option[SSLEngineResult] = None
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Option[SSLEngineResult] =
    if (open.get && peerNetData.hasRemaining) {
      engine match {
        case Some(e) =>
          implicit val en: SSLEngine = e
          val peerNet                = mergeWithUnderflowBuffer(underflowBuffer, peerNetData)
          val r                      = unwrap(peerNet, peerAppData.get)
          engineResultHolder.set(r)
          r.getStatus match {
            case OK =>
              exitRead(true)
              val extraBytes = (peerNet ne peerNetData) && peerNet.hasRemaining
              underflowBuffer.set(if (extraBytes) ByteBufferHelper.clone(peerNet) else ByteBufferHelper.ReadOnlyBuffer)
            case BUFFER_OVERFLOW =>
              val buffer = ByteBufferHelper.createBuffer(peerAppData.get().capacity(), e.getSession.getApplicationBufferSize)
              peerAppData.set(buffer)
              peerNetData.rewind()
            case BUFFER_UNDERFLOW =>
              handleUnderflow(peerNet, underflowBuffer)
            case _ =>
              closeConnection(readByteBuffer, writeByteBuffer, closeConn)
              closed(true)
              open.set(false)
          }
          if (engineResultHolder.get().getStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            Option(r)
          } else {
            readLoop(underflowBuffer)(peerNetData, peerAppData, open, exitRead, closed, engineResultHolder, Option(r))(
              readByteBuffer,
              writeByteBuffer,
              closeConn
            )
          }
        case _ =>
          peerAppData.get().put(peerNetData)
          result
      }
    } else {
      result
    }

  private def mergeWithUnderflowBuffer(underflowBuffer: AtomicReference[ByteBuffer], peerNetData: ByteBuffer) =
    underflowBuffer.get() match {
      case b if b.remaining() == 0 =>
        peerNetData
      case underflow =>
        ByteBufferHelper.mergeAndFlip(underflow, peerNetData)
    }

  private def unwrap(peerNetData: ByteBuffer, peerAppDataLocal: ByteBuffer)(implicit seq: Int, engine: SSLEngine): SSLEngineResult =
    try {
      logger.debug(s"unwrap $whoIAm ($seq) $peerNetData $peerAppDataLocal")
      val s = engine.unwrap(peerNetData, peerAppDataLocal)
      logger.debug(s"unwrap $whoIAm ($seq) $s $peerNetData")
      s
    } catch {
      handleError
    }

  private def wrap(myAppDataLocal: ByteBuffer, myNetData: ByteBuffer)(implicit seq: Int, engine: SSLEngine): SSLEngineResult =
    try {
      logger.debug(s"wrap $whoIAm ($seq) $myAppDataLocal $myNetData")
      val r = engine.wrap(myAppDataLocal, myNetData)
      logger.debug(s"wrap $whoIAm ($seq) $r $myAppDataLocal")
      r
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
          ByteBufferHelper.copy(myAppData, myNetData)
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
      logger.trace(s"$whoIAm write ($seq): $myNetData")
      writeByteBuffer(myNetData)
      writeToChannelLoop(status)(myNetData)(writeByteBuffer)
    }

}
