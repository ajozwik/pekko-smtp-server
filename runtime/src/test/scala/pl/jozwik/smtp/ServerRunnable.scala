package pl.jozwik.smtp

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.tls.NioSslServer
import pl.jozwik.smtp.DemoHelper.*

class ServerRunnable(port: Int) extends Runnable with AutoCloseable with StrictLogging {

  private lazy val server =
    new NioSslServer(TlsVersion, "0.0.0.0", port, keyStoreServerInputStream, TlsOpts.keystorePassword, TlsOpts.keyPassword)(
      trustStoreInputStream,
      TlsOpts.trustPassword
    )

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
