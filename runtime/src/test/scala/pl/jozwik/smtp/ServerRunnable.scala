package pl.jozwik.smtp

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.tls.{Buffers, NioSslServer, TlsOpts}
import pl.jozwik.smtp.DemoHelper.*

import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine

class ServerRunnable(port: Int, whoIAm: String = "server") extends Runnable with AutoCloseable with StrictLogging {

  private lazy val server =
    new NioSslServer(TlsVersion, "0.0.0.0", port, whoIAm, keyStoreServerInputStream, TlsOpts.keystorePassword, TlsOpts.keyPassword)(
      trustStoreInputStream,
      TlsOpts.trustPassword
    ) {
      // For UNDERFLOW test
      override protected def createBuffers(implicit e: SSLEngine): Buffers =
        Buffers(20, 20)

      override protected def createApplicationBuffer(implicit e: Option[SSLEngine]): ByteBuffer =
        ByteBuffer.allocate(2)

//      override protected def createPacketBuffer(implicit e: Option[SSLEngine]): ByteBuffer =
//        ByteBuffer.allocate(2)
    }

  def isBound: Boolean = server.isBound

  override def run(): Unit = {
    try {
      server.start()
    } catch {
      case e: Exception =>
        logger.error("Error:", e)
    }
  }

  override def close(): Unit = server.close()
}
