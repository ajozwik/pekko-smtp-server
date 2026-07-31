package pl.jozwik.smtp.tls

import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.util.{ByteBufferHelper, Constants, Utils}

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectableChannel, SelectionKey, Selector, SocketChannel}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory, TimeUnit}
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.*
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

object NioSslPeer {

  private val threadCount = new AtomicInteger(0)

  private val daemonThreadFactory: ThreadFactory = (r: Runnable) => {
    val thread = new Thread(r, s"nio-ssl-peer-${threadCount.incrementAndGet()}")
    thread.setDaemon(true)
    thread
  }

}

abstract class NioSslPeer(protocol: String)(keyPath: => InputStream, keystorePassword: String, keyPassword: String)(
    trustPath: => InputStream,
    trustPassword: String
) extends WithSslEngine
  with AutoCloseable {

  protected lazy val selector: Selector                               = SelectorProvider.provider.openSelector()
  protected val active: AtomicBoolean                                 = new AtomicBoolean(false)
  protected lazy val executor: ExecutorService                        = Executors.newCachedThreadPool(NioSslPeer.daemonThreadFactory)
  protected override def handshakeRepeatOnExtra: Set[HandshakeStatus] = Set.empty

  protected lazy val context: SSLContext =
    SSLContextFactory.createContext(protocol)(keyPath, keystorePassword, keyPassword)(trustPath, trustPassword)

  protected lazy val (applicationBufferSize: Int, packetBufferSize: Int) = {
    val s = context.createSSLEngine().getSession
    val p = s.getPacketBufferSize
    val a = s.getApplicationBufferSize
    s.invalidate()
    (a, p)
  }

  def close(): Unit = {
    logger.trace(s"$whoIAm: Close connection with the $whoContactMe...")
    active.set(false)
    Utils.ignoreError {
      selector.wakeup()
      Utils.ignoreError {
        val keys = selector.keys()
        keys.forEach { k =>
          if (k.isValid) {
            k.cancel()
          }
        }
      }
      selector.close()
    }
    executor.shutdown()
    closeImpl()
    executor.awaitTermination(2, TimeUnit.SECONDS)
  }

  protected def closeImpl(): Unit

  @tailrec
  protected final def mainLoop(): Unit =
    if (isActive && selector.isOpen) {
      Try {
        selector.select(t => handleKey(t))
      } match {
        case Success(_) =>
          mainLoop()
        case Failure(e) =>
          logger.error(s"Error in mainLoop", e)
          close()
      }
    } else {
      logger.trace(s"$whoIAm: Leave mainLoop")
    }

  protected def setEngineModeAndStartHandshake(a: Attachment, useClientMode: Boolean)(
      selectionKey: SelectionKey
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, close: () => Unit)(implicit seq: Int, e: SSLEngine): Unit = {
    val attachment: Attachment = setEngineModeAndStartHandshake(a, useClientMode)
    selectionKey.attach(attachment)
    doHandshake(attachment)(readByteBuffer, writeByteBuffer, close)
  }

  protected def closeConnection(sc: SocketChannel): Unit

  private def handleKey(key: SelectionKey): Unit =
    if (key.isValid) {
      key.channel() match {
        case sc: SocketChannel =>
          implicit val socket: SocketChannel = sc
          implicit val seq: Int              = iterator.next()
          key.attachment() match {
            case a @ Attachment(Some(engine), _, status, _) if status.get() != HandshakeStatus.NOT_HANDSHAKING && status.get() != HandshakeStatus.FINISHED =>
              implicit val e: SSLEngine = engine
              doHandshake(a)(sc.read, writeToOutputBuffer, () => closeConnection(sc))
            case a @ Attachment(e, b, _, open) =>
              implicit val o: AtomicBoolean                             = open
              implicit val underflowBuffer: AtomicReference[ByteBuffer] = b.underflowBuffer
              implicit val en                                           = e
              handleReadKeyLoop(key, a)

            case r =>
              sys.error(s"Attachment: $r")
          }
        case e =>
          handleKeyImpl(key)(e)

      }
    }

  private def handleReadKeyLoop(key: SelectionKey, a: Attachment)(implicit
      seq: Int,
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean,
      e: Option[SSLEngine],
      sc: SocketChannel
  ): Unit = {
    val peerNetData = createPacketBuffer(e)
    val count       = sc.read(peerNetData)
    peerNetData.flip()
    if (count > 0) {
      readAndResponse(peerNetData)(a.clearBuffers, key)(sc.read, writeToOutputBuffer, () => closeConnection(sc))
      handleReadKeyLoop(key, a)
    }
  }

  protected def writeToOutputBuffer(b: ByteBuffer)(implicit sc: SocketChannel): Unit =
    sc.write(b)

  private def readAndResponse(peerNetData: ByteBuffer)(
      a: Attachment,
      key: SelectionKey
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): Unit = {
    implicit val en: Option[SSLEngine] = a.engine
    handleRead(peerNetData)(readByteBuffer, writeByteBuffer, closeConn) match {
      case (Some(message), _) =>
        val bs = ByteBufferHelper.toByteStringImmutable(message)
        if (bs.endsWith(Constants.DelimiterBytes)) {
          val b = ByteBufferHelper.merge(underflowBuffer.get(), message.flip())
          val m = ByteBufferHelper.toByteString(b)
          readResponse(a)(m, key)(readByteBuffer, writeByteBuffer, closeConn)
          underflowBuffer.set(ByteBufferHelper.ReadOnlyBuffer)
        } else {
          underflowBuffer.accumulateAndGet(message.flip(), ByteBufferHelper.mergeAndFlip)
          ()
        }

      case (_, s) =>
        logger.trace(s"$whoIAm: ($seq) No message received. ${s.map(_.getHandshakeStatus)}")
        if (s.exists(_.getHandshakeStatus == HandshakeStatus.FINISHED)) {
          peerHandshakeFinished(peerNetData)
        }

    }
  }

  protected def readResponse(
      a: Attachment
  )(message: ByteString, key: SelectionKey)(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      open: AtomicBoolean
  ): Unit

  protected def handleKeyImpl(key: SelectionKey)(ch: SelectableChannel): Unit

  private def isActive: Boolean = active.get

}
