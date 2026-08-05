package pl.jozwik.smtp.tls

import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.util.{ByteBufferHelper, Constants, SmtpCodes, SmtpResponses}

import java.io.{FileInputStream, InputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{SelectableChannel, SelectionKey, ServerSocketChannel, SocketChannel}
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLEngine

class NioSslServer(
    protocol: String,
    hostAddress: String,
    port: Int,
    override protected val whoIAm: String = "server",
    keyPath: => InputStream = new FileInputStream(EphemeralTls.serverKeyStoreFile),
    keystorePassword: String = TlsOpts.keystorePassword,
    keyPassword: String = TlsOpts.keystorePassword
)(trustPath: => InputStream = new FileInputStream(EphemeralTls.trustStoreFile), trustPassword: String = TlsOpts.trustPassword)
  extends NioSslPeer(protocol)(keyPath, keystorePassword, keyPassword)(trustPath, trustPassword)
  with WithSslEngineServer {

  private val serverSocketChannel = ServerSocketChannel.open()
  serverSocketChannel.configureBlocking(false)
  private val serverSocker = serverSocketChannel.socket()

  try {
    serverSocker.bind(new InetSocketAddress(hostAddress, port))
  } catch {
    case e: Exception =>
      logger.error(s"$whoIAm Failed to bind server socket channel. $port", e)
      System.exit(1)
  }

  serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)

  active.set(true)

  def isBound: Boolean = serverSocker.isBound

  def start(): Unit = {
    logger.trace(s"$whoIAm Initialized and waiting for new connections... $port")
    mainLoop()
    logger.trace("Goodbye!")
  }

  protected def closeImpl(): Unit = {
    logger.trace(s"$whoIAm Will now close server...")
    serverSocker.close()
    serverSocketChannel.close()
  }

  // For UNDERFLOW test
  override protected def createBuffers(implicit e: SSLEngine): Buffers =
    Buffers(20, 20)

//  override protected def createApplicationBuffer(implicit e: Option[SSLEngine]): ByteBuffer =
//    ByteBuffer.allocate(20)
//
//  override protected def createPacketBuffer(implicit e: Option[SSLEngine]): ByteBuffer =
//    ByteBuffer.allocate(20)

  private def accept(serverSocketChannel: ServerSocketChannel): Unit = Option(serverSocketChannel.accept()).foreach { socketChannel =>
    socketChannel.configureBlocking(false)
    logger.trace(s"$whoIAm New connection request! ${socketChannel.getRemoteAddress}")
    socketChannel.register(selector, SelectionKey.OP_READ, Attachment.empty)
    implicit val sc: SocketChannel     = socketChannel
    implicit val seq: Int              = iterator.next()
    implicit val en: Option[SSLEngine] = None
    write(ByteBufferHelper.toByteBuffer(s"${SmtpCodes.SERVICE_READY} SMTP DEMO", Constants.CrLf))(socketChannel.read, writeToOutputBuffer, () => socketChannel.close())
  }

  override protected def handleKeyImpl(key: SelectionKey)(ch: SelectableChannel): Unit = ch match {
    case ssc: ServerSocketChannel =>
      accept(ssc)
    case _ =>
      logger.error(s"Unexpected channel: $ch")
  }

  override protected def readResponse(
      a: Attachment
  )(message: ByteString, key: SelectionKey)(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      open: AtomicBoolean
  ): Unit = {
    val str = message.utf8String.trim
    if (str.nonEmpty) {
      logger.trace(s"$whoIAm: ($seq) Received message from the $whoContactMe: $str")
    }
    if (open.get()) {
      implicit val en: Option[SSLEngine] = a.engine
      str match {
        case Constants.STARTTLS =>
          write(ByteBufferHelper.toByteBuffer(s"${SmtpResponses.TLS_SUPPORTED_RESPONSE}", Constants.CrLf))(readByteBuffer, writeByteBuffer, closeConn)
          implicit val e: SSLEngine = context.createSSLEngine()
          setEngineModeAndStartHandshake(a, useClientMode = false)(key)(readByteBuffer, writeByteBuffer, closeConn)
        case _ =>
          write(ByteBufferHelper.toByteBuffer(s"Hello! I am your $whoIAm! ${a.engine.isDefined}", Constants.CrLf))(readByteBuffer, writeByteBuffer, closeConn)
      }

    }
  }

  override protected def closeConnection(sc: SocketChannel): Unit = sc.close()
}
