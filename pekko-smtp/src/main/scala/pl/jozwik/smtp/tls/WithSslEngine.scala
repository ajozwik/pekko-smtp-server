package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.tls.TlsHelper.{toApplicationBufferSize, toPacketBufferSize}
import pl.jozwik.smtp.util.{ByteBufferHelper, Utils}

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status.{BUFFER_OVERFLOW, BUFFER_UNDERFLOW, OK}
import javax.net.ssl.{SSLEngine, SSLEngineResult, SSLException}
import scala.annotation.tailrec

object WithSslEngine {
  private val handshakeRepeatOn = Set(HandshakeStatus.NEED_TASK, HandshakeStatus.FINISHED, HandshakeStatus.NEED_WRAP)
}

trait WithSslEngine extends WithSequenceIterator with StrictLogging {

  protected def applicationBufferSize: Int

  protected def closeConnection(writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Unit = {
    engine.foreach { implicit e =>
      val a = TlsEngineState.empty
      doHandshake(a, ByteBufferHelper.createEmptyBuffer)(writeByteBuffer, close)
      e.closeOutbound()
    }

    close()
  }

  @tailrec
  protected final def doHandshake(
      a: TlsEngineState,
      peerNetData: ByteBuffer,
      engineResultHolder: AtomicReference[SSLEngineResult] =
        new AtomicReference[SSLEngineResult](TlsHelper.failedHandshakeResult(HandshakeStatus.NOT_HANDSHAKING))
  )(writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      e: SSLEngine,
      seq: Int
  ): TlsEngineState = {
    import a.*
    val b = a.buffers
    if (open.get && TlsHelper.isHandshaking(handshakeStatus.get)) {
      val lastHandshakeStatus = handshakeStatus.get
      logger.trace(s"$whoIAm: ($seq) doHandshake: $peerNetData $lastHandshakeStatus ${e.getHandshakeStatus} underflow=${b.underflowBuffer}")
      lastHandshakeStatus match {
        case HandshakeStatus.NEED_UNWRAP =>
          val bytesCount = peerNetData.remaining()
          if (bytesCount > 0) {
            val peerNet = mergeWithUnderflowBuffer(b.underflowBuffer, peerNetData)
            val r       = unwrap(peerNet, b.peerAppDataLocal.get())
            handshakeStatus.set(r.getHandshakeStatus)
            engineResultHolder.set(r)
            r.getStatus match {
              case OK =>
                val extraBytes = (peerNet ne peerNetData) && peerNet.hasRemaining
                b.underflowBuffer.set(if (extraBytes) ByteBufferHelper.clone(peerNet) else ByteBufferHelper.ReadOnlyBuffer)
              case BUFFER_UNDERFLOW =>
                handleUnderflow(peerNet, b.underflowBuffer)
              case _ =>
                if (e.isOutboundDone) {
                  open.set(false)
                } else {
                  e.closeOutbound()
                  handshakeStatus.set(e.getHandshakeStatus)
                }
            }
          }

        case HandshakeStatus.NEED_WRAP =>
          b.myNetData.get.clear
          val result = wrap(b.myAppDataLocal, b.myNetData.get())
          engineResultHolder.set(result)
          handshakeStatus.set(result.getHandshakeStatus)
          result.getStatus match {
            case OK =>
              writeToChannel(result.getHandshakeStatus)(b.myNetData.get())(writeByteBuffer)
            case BUFFER_OVERFLOW =>
              val newBuffer = ByteBufferHelper.createBuffer(b.myNetData.get().capacity(), e.getSession.getPacketBufferSize)
              b.myNetData.set(newBuffer)
            case _ =>
              handshakeStatus.set(e.getHandshakeStatus)
              implicit val en: Option[SSLEngine] = Option(e)
              handleEndOfStream(writeByteBuffer, close)
          }
        case _ => // HandshakeStatus.NEED_TASK
          TlsHelper.runDelegatedTasks
          handshakeStatus.set(e.getHandshakeStatus)

      }
      logger.trace(s"$whoIAm peerNetDataBefore ($seq): $peerNetData  ${b.underflowBuffer} $handshakeStatus ${engineResultHolder.get().getStatus}")

      if (engineResultHolder.get().getStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
        a
      } else if (
        (peerNetData.remaining() > 0 && handshakeStatus.get == HandshakeStatus.NEED_UNWRAP) ||
        WithSslEngine.handshakeRepeatOn.contains(
          handshakeStatus.get
        ) || lastHandshakeStatus == HandshakeStatus.NEED_TASK || lastHandshakeStatus == HandshakeStatus.FINISHED ||
        engineResultHolder.get().getStatus == SSLEngineResult.Status.BUFFER_OVERFLOW
      ) {
        logger.trace(s"$whoIAm peerNetData ($seq): $peerNetData  ${b.underflowBuffer} $handshakeStatus ${engineResultHolder.get().getStatus}")
        doHandshake(a, peerNetData, engineResultHolder)(writeByteBuffer, close)
      } else {
        a
      }
    } else {
      if (handshakeStatus.get == HandshakeStatus.FINISHED) {
        ownHandshakeFinished(peerNetData)
      }
      a
    }

  }

  protected def handleRead(peerNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): (Option[ByteBuffer], Option[SSLEngineResult])

  protected def ownHandshakeFinished(remaining: ByteBuffer): Unit

  protected def packetBufferSize: Int

  protected def read(peerNetData: ByteBuffer)(
      exitRead: Boolean => Unit,
      closed: Boolean => Unit = Utils.fakeCallT
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val bytes = peerNetData.remaining()
    logger.trace(s"$whoIAm: Read $bytes ($seq) from a $whoContactMe, engine=${engine.map(_.getHandshakeStatus)}")
    bytes match {
      case 0 =>
        (Option(ByteBufferHelper.ReadOnlyBuffer), None)
      case _ =>
        readBuffer(underflowBuffer)(peerNetData, open, exitRead, closed)(writeByteBuffer, closeConn)
    }
  }

  protected def setEngineModeAndStartHandshake(a: TlsEngineState, useClientMode: Boolean)(implicit e: SSLEngine): TlsEngineState = {
    e.setUseClientMode(useClientMode)
    e.beginHandshake()
    val b = createBuffers
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
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int, engine: Option[SSLEngine]): Unit = {
    val myAppData = ByteBufferHelper.clone(message)
    val myNetData = new AtomicReference(createPacketBuffer)
    writeMessage(myAppData, myNetData)(writeByteBuffer, closeConn)
  }

  private def handleUnderflow(peerNet: ByteBuffer, underflowBuffer: AtomicReference[ByteBuffer])(implicit
      seq: Int
  ): Unit = {
    logger.trace(s"$whoIAm handleUnderflow ($seq) $peerNet")
    underflowBuffer.set(ByteBufferHelper.clone(peerNet))
  }

  private def handleEndOfStream(writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Unit = {
    Utils.ignoreError {
      engine.foreach { e =>
        e.closeInbound()
      }
    }
    closeConnection(writeByteBuffer, close)
  }

  private def handleError(implicit engine: SSLEngine, seq: Int): PartialFunction[Throwable, SSLEngineResult] = { case exp: SSLException =>
    logger.error(
      s"$whoIAm: ($seq) Will try to properly close connection...",
      exp
    )
    engine.closeOutbound()
    throw exp
  }

  private def readBuffer(underflowBuffer: AtomicReference[ByteBuffer])(
      peerNetData: ByteBuffer,
      open: AtomicBoolean,
      exitRead: Boolean => Unit,
      closed: Boolean => Unit
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val peerAppData = new AtomicReference(createApplicationBuffer)
    val l           = readLoop(underflowBuffer)(peerNetData, peerAppData, open, exitRead, closed)(writeByteBuffer, closeConn)
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
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): Option[SSLEngineResult] =
    if (open.get && peerNetData.hasRemaining) {
      logger.trace(s"$whoIAm readLoop ($seq): $peerNetData $underflowBuffer e=${engine.isDefined}")
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
              underflowBuffer.get().rewind()
            case BUFFER_UNDERFLOW =>
              handleUnderflow(peerNet, underflowBuffer)
            case _ =>
              closeConnection(writeByteBuffer, closeConn)
              closed(true)
              open.set(false)
          }
          if (engineResultHolder.get().getStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            Option(r)
          } else {
            readLoop(underflowBuffer)(peerNetData, peerAppData, open, exitRead, closed, engineResultHolder, Option(r))(
              writeByteBuffer,
              closeConn
            )
          }
        case _ =>
          ByteBufferHelper.copy(peerNetData, peerAppData)
          result
      }
    } else {
      result
    }

  private def mergeWithUnderflowBuffer(underflowBuffer: AtomicReference[ByteBuffer], peerNetData: ByteBuffer)(implicit seq: Int) =
    underflowBuffer.get() match {
      case b if b.remaining() == 0 =>
        peerNetData
      case underflow =>
        logger.trace(s"$whoIAm mergeWithUnderflowBuffer ($seq): $underflow $peerNetData")
        val b = ByteBufferHelper.mergeAndFlip(underflow, peerNetData)
        logger.trace(s"$whoIAm mergeWithUnderflowBuffer ($seq): $b")
        b
    }

  private def unwrap(peerNetData: ByteBuffer, peerAppDataLocal: ByteBuffer)(implicit seq: Int, engine: SSLEngine): SSLEngineResult =
    try {
      logger.trace(s"unwrap $whoIAm ($seq) $peerNetData $peerAppDataLocal")
      val s = engine.unwrap(peerNetData, peerAppDataLocal)
      logger.trace(s"unwrap $whoIAm ($seq) $s $peerNetData")
      s
    } catch {
      handleError
    }

  private def wrap(myAppDataLocal: ByteBuffer, myNetData: ByteBuffer)(implicit seq: Int, engine: SSLEngine): SSLEngineResult =
    try {
      logger.trace(s"wrap $whoIAm ($seq) $myAppDataLocal $myNetData")
      val r = engine.wrap(myAppDataLocal, myNetData)
      logger.trace(s"wrap $whoIAm ($seq) $r $myAppDataLocal")
      r
    } catch {
      handleError
    }

  @tailrec
  private def writeMessage(
      myAppData: ByteBuffer,
      myNetData: AtomicReference[ByteBuffer]
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int, engine: Option[SSLEngine]): Unit =
    if (myAppData.hasRemaining) {
      val continue = new AtomicReference(true)
      engine match {
        case Some(e) =>
          implicit val en: SSLEngine = e
          myNetData.get.clear()
          val result = wrap(myAppData, myNetData.get)
          result.getStatus match {
            case OK =>
              writeToChannel(engine.map(_.getHandshakeStatus).getOrElse(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING))(myNetData.get())(writeByteBuffer)
              continue.set(myAppData.hasRemaining)
            case BUFFER_OVERFLOW =>
              val buffer = ByteBufferHelper.createBuffer(myNetData.get().capacity(), e.getSession.getPacketBufferSize)
              myNetData.set(buffer)
            case _ => // CLOSED
              closeConnection(writeByteBuffer, closeConn)
              continue.set(false)
          }
        case _ =>
          ByteBufferHelper.copyAndFlip(myAppData, myNetData)
          writeToChannelLoop(engine.map(_.getHandshakeStatus).getOrElse(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING))(myNetData.get())(writeByteBuffer)
      }
      if (continue.get) {
        writeMessage(myAppData, myNetData)(writeByteBuffer, closeConn)
      }
    }

  private def writeToChannel(
      status: SSLEngineResult.HandshakeStatus
  )(myNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit = {
    myNetData.flip()
    writeToChannelLoop(status)(myNetData)(writeByteBuffer)
  }

  @tailrec
  private def writeToChannelLoop(
      status: SSLEngineResult.HandshakeStatus
  )(myNetData: ByteBuffer)(writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit =
    if (myNetData.hasRemaining) {
      logger.trace(s"$whoIAm write ($seq): $status $myNetData")
      writeByteBuffer(myNetData)
      writeToChannelLoop(status)(myNetData)(writeByteBuffer)
    }

}
