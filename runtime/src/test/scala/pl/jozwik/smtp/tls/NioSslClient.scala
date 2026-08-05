package pl.jozwik.smtp.tls

import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.util.{ByteBufferHelper, Constants, Utils}

import java.io.{FileInputStream, InputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{SelectableChannel, SelectionKey, SocketChannel}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}
import javax.net.ssl.SSLEngine
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
  extends NioSslPeer(protocol: String)(keyPath, keystorePassword, keyPassword)(trustPath, trustPassword)
  with WithSslEngineClientBase {

  override protected val whoContactMe: String   = "server"
  private lazy val socketChannel: SocketChannel = SocketChannel.open()
  private lazy val selectionKey: SelectionKey   = socketChannel.register(selector, SelectionKey.OP_READ, Attachment.empty)
  private val lastRead                          = new AtomicReference(ByteString.empty)
  private val readLock: Lock                    = new ReentrantLock()
  private val readCondition: Condition          = readLock.newCondition
  private val engineLock: Lock                  = new ReentrantLock()
  private val engineCondition: Condition        = engineLock.newCondition

  private implicit def sc: SocketChannel = socketChannel

  def connect(): Unit = {
    socketChannel.configureBlocking(false)
    socketChannel.connect(new InetSocketAddress(remoteHost, remotePort))
    waitForConnect()

  }

  def getLastRead: ByteString =
    lastRead.get()

  def waitForRead(): ByteString = {
    UtilsHelper.await(readLock, readCondition) {}
    readAndClear
  }

  private def readAndClear =
    lastRead.getAndSet(ByteString.empty)

  def startTls(): ByteString = {
    val b = writeAndWaitForRead(Utils.withEndOfLine(s"${Constants.STARTTLS}"))
    logger.trace(s"startTls-response: ${b.utf8String}")
    createEngine()
    b
  }

  def writeAndWaitForRead(message: String): ByteString = {
    UtilsHelper.await(readLock, readCondition) {
      logger.trace(s"$whoIAm writeAndWaitForRead ${message.trim} lastRead=${getLastRead.utf8String.trim}")
      implicit val seq: Int             = iterator.next()
      implicit val e: Option[SSLEngine] = waitForEngine
      write(ByteBufferHelper.toByteBuffer(message))(socketChannel.read, writeToOutputBuffer, () => socketChannel.close())
    }
    readAndClear
  }

  def writeMessage(message: String): Unit = {
    implicit val seq: Int             = iterator.next()
    implicit val e: Option[SSLEngine] = waitForEngine
    write(ByteBufferHelper.toByteBuffer(message))(
      socketChannel.read,
      writeToOutputBuffer,
      () => socketChannel.close()
    )
  }

  def writeSplitAndWaitForRead(message: String): ByteString = {
    UtilsHelper.await(readLock, readCondition) {
      implicit val seq: Int             = iterator.next()
      implicit val e: Option[SSLEngine] = waitForEngine
      write(ByteBufferHelper.toByteBuffer(message))(
        socketChannel.read,
        simulatePartialWrite,
        () => socketChannel.close()
      )
    }
    readAndClear
  }

  protected override def writeToOutputBuffer(b: ByteBuffer)(implicit sc: SocketChannel): Unit =
    simulatePartialWrite(b)

  private def simulatePartialWrite(b: ByteBuffer): Unit = {
    val (x, y) = ByteBufferHelper.split(b, b.limit() / 2)
    sc.write(x)
    TimeUnit.MILLISECONDS.sleep(50)
    sc.write(y)
    b.position(b.limit())
  }

  private def createEngine(): Unit = {
    implicit val e: SSLEngine = context.createSSLEngine(remoteHost, remotePort)
    implicit val seq: Int     = iterator.next()
    setEngineModeAndStartHandshake(selectionKey.attachment().asInstanceOf[Attachment], useClientMode = true)(selectionKey)(
      socketChannel.read,
      writeToOutputBuffer,
      () => close()
    )
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
    val a                             = fromSelectionKey
    implicit val e: Option[SSLEngine] = a.engine
    Utils.ignoreError {
      implicit val seq: Int = -1
      closeConnection(
        socketChannel.read,
        writeToOutputBuffer,
        { () =>
          Utils.ignoreError {
            if (selectionKey.isValid) {
              selectionKey.cancel()
            }
            socketChannel.shutdownInput()
            socketChannel.shutdownOutput()
          }
          socketChannel.close()
        }
      )
    }
    logger.trace(s"$whoIAm: closed the channel")
  }

  @tailrec
  private def waitForConnect(): Unit =
    if (socketChannel.finishConnect()) {
      logger.trace(s"$whoIAm Connected with.. ${socketChannel.socket().getRemoteSocketAddress}")
      executor.execute { () =>
        selectionKey
        active.set(true)
        mainLoop()
      }
    } else {
      logger.trace(s"$whoIAm Waiting for connection... ${socketChannel.socket().getRemoteSocketAddress}")
      waitForConnect()
    }

  override protected def handleKeyImpl(key: SelectionKey)(ch: SelectableChannel): Unit =
    logger.error(s"$whoIAm Unexpected channel: $ch")

  override protected def peerHandshakeFinished(remaining: ByteBuffer): Unit = {
    logger.trace(s"$whoIAm: Handshake finished peer  $remaining")
    UtilsHelper.signal(readLock, readCondition) {
      logger.trace(s"$whoIAm: Handshake finished readLock")
      UtilsHelper.signal(engineLock, engineCondition) {}
    }
  }

  override protected def ownHandshakeFinished(remaining: ByteBuffer): Unit =
    UtilsHelper.signal(engineLock, engineCondition) {
      logger.trace(s"$whoIAm: Handshake finished engine $remaining")
    }

  override protected def readResponse(
      a: Attachment
  )(message: ByteString, key: SelectionKey)(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      open: AtomicBoolean
  ): Unit =
    if (message.nonEmpty)
      UtilsHelper.signal(readLock, readCondition) {
        logger.trace(s"$whoIAm readResponse: ${message.utf8String}")
        lastRead.set(message)
      }
    else {
      logger.debug("Empty")
    }

  override protected def closeConnection(sc: SocketChannel): Unit = close()
}
