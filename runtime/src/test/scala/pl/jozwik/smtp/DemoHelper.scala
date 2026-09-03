package pl.jozwik.smtp

import pl.jozwik.smtp.tls.{EphemeralTls, TlsHelper}

import java.io.{FileInputStream, InputStream}

object DemoHelper {
  val TlsVersion: String = TlsHelper.TLSv1_3
  val Port               = 9222

  def keyStoreClientInputStream: InputStream = new FileInputStream(EphemeralTls.clientKeyStoreFile)
  def keyStoreServerInputStream: InputStream = new FileInputStream(EphemeralTls.serverKeyStoreFile)
  def trustStoreInputStream: InputStream     = new FileInputStream(EphemeralTls.trustStoreFile)
}
