package pl.jozwik.smtp.tls

import pl.jozwik.smtp.util.Utils

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{ SelectableChannel, SelectionKey, Selector, SocketChannel }
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ ExecutorService, Executors, TimeUnit }
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.*
import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

abstract class NioSslPeer(protocol: String)(keyPath: => InputStream, keystorePassword: String, keyPassword: String)(
    trustPath: => InputStream,
    trustPassword: String
) extends WithSslEngine
  with AutoCloseable {

  protected lazy val selector: Selector                               = SelectorProvider.provider.openSelector()
  protected val active: AtomicBoolean                                 = new AtomicBoolean(false)
  protected lazy val executor: ExecutorService                        = Executors.newCachedThreadPool()
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
    Utils.ignoreErrors {
      selector.wakeup()
      val keys = selector.keys()
      keys.forEach(_.cancel())
      selector.close()
    }
    active.set(false)
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

  protected def setEngineModeAndStartHandshake(e: SSLEngine, a: Attachment, useClientMode: Boolean)(
      selectionKey: SelectionKey
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit)(implicit seq: Int): Unit = {
    val attachment = setEngineModeAndStartHandshake(a, e, useClientMode)
    selectionKey.attach(attachment)
    doHandshake(e, attachment.buffers, attachment.handshakeStatus, attachment.open)(readByteBuffer, writeByteBuffer)
  }

  protected def closeConnection(sc: SocketChannel): Unit

  private def handleKey(key: SelectionKey): Unit =
    if (key.isValid) {
      key.channel() match {
        case sc: SocketChannel =>
          implicit val seq: Int = iterator.next()
          key.attachment() match {
            case Attachment(Some(engine), buffers, status, result)
                if status.get() != HandshakeStatus.NOT_HANDSHAKING && status.get() != HandshakeStatus.FINISHED =>
              logger.trace(s"$whoIAm: ($seq)  Handshake is still in progress: ${status.get()}")
              doHandshake(engine, buffers, status, result)(sc.read, b => sc.write(b))
            case a @ Attachment(_, _, _, open) =>
              readAndResponse(a.clearBuffers, key, open.get())(sc.read, b => sc.write(b), () => closeConnection(sc))
            case r =>
              sys.error(s"Attachment: $r")
          }
        case e =>
          handleKeyImpl(key)(e)

      }
    }

  private def readAndResponse(
      a: Attachment,
      key: SelectionKey,
      open: Boolean
  )(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit seq: Int): Unit =
    handleRead(a.engine)(readByteBuffer, closeConn) match {
      case (Some(message), _) =>
        readResponse(a, open)(message, key)(readByteBuffer, writeByteBuffer, closeConn)
      case (_, s) =>
        logger.trace(s"$whoIAm: ($seq) No message received. ${s.map(_.getHandshakeStatus)}")
        if (s.exists(_.getHandshakeStatus == HandshakeStatus.FINISHED)) {
          peerHandshakeFinished()
        }

    }

  protected def readResponse(
      a: Attachment,
      open: Boolean
  )(message: ByteBuffer, key: SelectionKey)(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int
  ): Unit

  protected def handleKeyImpl(key: SelectionKey)(ch: SelectableChannel): Unit

  private def isActive: Boolean = active.get

}
