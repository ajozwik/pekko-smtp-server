package pl.jozwik.smtp

import pl.jozwik.smtp.tls.EphemeralTls

import java.io.{FileInputStream, InputStream}

object DemoHelper {
  private val Tls13      = "TLSv1.3"
  val TlsVersion: String = Tls13
  val Port               = 9222

  def keyStoreClientInputStream: InputStream = new FileInputStream(EphemeralTls.clientKeyStoreFile)
  def keyStoreServerInputStream: InputStream = new FileInputStream(EphemeralTls.serverKeyStoreFile)
  def trustStoreInputStream: InputStream     = new FileInputStream(EphemeralTls.trustStoreFile)
}
