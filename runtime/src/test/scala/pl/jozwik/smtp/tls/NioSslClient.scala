package pl.jozwik.smtp.tls

import pl.jozwik.smtp.TlsOpts
import pl.jozwik.smtp.util.{ByteBufferHelper, Constants, Utils}

import java.io.{FileInputStream, InputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{SelectableChannel, SelectionKey, SocketChannel}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}
import javax.net.ssl.{SSLEngine, SSLEngineResult}
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import scala.annotation.tailrec

class NioSslClient(
    protocol: String,
    remoteHost: String,
    remotePort: Int,
    override protected val whoIAm: String,
    keyPath: => InputStream = new FileInputStream(EphemeralTls.clientKeyStoreFile),
    keystorePassword: String = TlsOpts.clientKeystorePassword,
    keyPassword: String = TlsOpts.clientKeystorePassword
)(trustPath: => InputStream = new FileInputStream(EphemeralTls.trustStoreFile), trustPassword: String = TlsOpts.trustPassword)
  extends NioSslPeer(protocol: String)(keyPath, keystorePassword, keyPassword)(trustPath, trustPassword) {

  override protected val whoContactMe: String   = "server"
  private lazy val socketChannel: SocketChannel = SocketChannel.open()
  private lazy val selectionKey                 = socketChannel.register(selector, SelectionKey.OP_READ, Attachment.empty)
  private val lastRead                          = new AtomicReference[ByteBuffer](ByteBufferHelper.ReadOnlyBuffer)
  private val readLock: Lock                    = new ReentrantLock()
  private val readCondition: Condition          = readLock.newCondition
  private val engineLock: Lock                  = new ReentrantLock()
  private val engineCondition: Condition        = engineLock.newCondition

  private def createEngine(): Unit = {
    implicit val e: SSLEngine = context.createSSLEngine(remoteHost, remotePort)
    implicit val seq: Int     = iterator.next()
    setEngineModeAndStartHandshake(selectionKey.attachment().asInstanceOf[Attachment], useClientMode = true)(selectionKey)(
      socketChannel.read,
      b => socketChannel.write(b)
    )
  }

  def startTls(): ByteBuffer = {
    val b = writeAndWaitForRead(Utils.withEndOfLine(s"${Constants.STARTTLS}"))
    createEngine()
    b
  }

  def connect(): Unit = {
    socketChannel.configureBlocking(false)
    socketChannel.connect(new InetSocketAddress(remoteHost, remotePort))
    active.set(true)
    waitForConnect()
  }

  def writeAndWaitForRead(message: String): ByteBuffer = {
    UtilsHelper.await(readLock, readCondition) {
      logger.trace(s"writeAndWaitForRead ${message.trim} ${ByteBufferHelper.toString(lastRead.get()).trim}")
      val e = waitForEngine
      write(e, ByteBufferHelper.toByteBuffer(message))(b => socketChannel.write(b), () => socketChannel.close())(iterator.next())
    }
    lastRead.getAndSet(ByteBufferHelper.ReadOnlyBuffer)
  }

  private def waitForEngine: Option[SSLEngine] = fromSelectionKey match {
    case Attachment(e, _, s, _) if s.get() == HandshakeStatus.NOT_HANDSHAKING || s.get() == HandshakeStatus.FINISHED =>
      e
    case a =>
      UtilsHelper.await(engineLock, engineCondition) {
        a.engine
      }

  }

  private def fromSelectionKey: Attachment =
    selectionKey.attachment() match {
      case a: Attachment =>
        a
      case _ =>
        sys.error(s"Attachment: ${selectionKey.attachment()}")
    }

  protected def closeImpl(): Unit = {
    logger.trace(s"$whoIAm: closing the channel")
    UtilsHelper.signal(readLock, readCondition) {}
    UtilsHelper.signal(engineLock, engineCondition) {}
    val a = fromSelectionKey
    Utils.ignoreErrors {
      closeConnection(a.engine) { () =>
        socketChannel.shutdownInput()
        socketChannel.shutdownOutput()
        socketChannel.close()
      }
    }
    logger.trace(s"$whoIAm: closed the channel")
  }

  protected override def handleRead(
      engine: Option[SSLEngine]
  )(readByteBuffer: ByteBuffer => Int, closeConn: () => Unit)(implicit seq: Int): (Option[ByteBuffer], Option[SSLEngineResult]) =
    read(engine, _ => (), _ => ())(readByteBuffer, closeConn)

  @tailrec
  private def waitForConnect(): Unit =
    if (socketChannel.finishConnect()) {
      executor.execute { () =>
        selectionKey
        mainLoop()
      }
    } else {
      logger.trace(s"Waiting for connection... ${socketChannel.socket().getRemoteSocketAddress}")
      waitForConnect()
    }

  override protected def handleKeyImpl(key: SelectionKey)(ch: SelectableChannel): Unit =
    logger.error(s"Unexpected channel: $ch")

  override protected def peerHandshakeFinished(): Unit = {
    logger.trace(s"$whoIAm: Handshake finished peer")
    UtilsHelper.signal(readLock, readCondition) {
      logger.trace(s"$whoIAm: Handshake finished readLock")
      UtilsHelper.signal(engineLock, engineCondition) {}
    }
  }

  override protected def ownHandshakeFinished(): Unit =
    UtilsHelper.signal(engineLock, engineCondition) {
      logger.trace(s"$whoIAm: Handshake finished engine")
    }

  override protected def readResponse(
      a: Attachment,
      open: Boolean
  )(message: ByteBuffer, key: SelectionKey)(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int
  ): Unit = UtilsHelper.signal(readLock, readCondition) {
    lastRead.set(message)
  }

  override protected def closeConnection(sc: SocketChannel): Unit = close()
}
