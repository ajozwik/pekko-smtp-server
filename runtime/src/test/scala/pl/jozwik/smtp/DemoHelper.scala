package pl.jozwik.smtp

import java.io.InputStream

object DemoHelper {
  private val Tls13      = "TLSv1.3"
  val TlsVersion: String = Tls13
  val Port               = 9222

  def keyStoreClientInputStream: InputStream = getClass.getResourceAsStream("/tls13/client.p12")
  def keyStoreServerInputStream: InputStream = getClass.getResourceAsStream("/tls13/server.p12")
  def trustStoreInputStream: InputStream     = getClass.getResourceAsStream("/tls13/trustedCerts.jks")
}
