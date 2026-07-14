package pl.jozwik.smtp

import pl.jozwik.smtp.util.RuntimeConstants

import java.io.{ FileInputStream, InputStream }
import java.util.concurrent.Callable

object TlsOpts {
  val clientKeystorePassword: String = RuntimeConstants.clientKeyStorePassword.valueOrDefault("clientpass")
  val keystorePassword: String       = RuntimeConstants.keyStorePassword.valueOrDefault("changeit")
  val keyPassword: String            = keystorePassword
  val trustPassword: String          = RuntimeConstants.trustStorePassword.valueOrDefault("truststore")

  private def protocol: String       = RuntimeConstants.tlsProtocol.valueOrDefault("TLSv1.3")
  private def fileStorePath          = RuntimeConstants.keyStoreFile.propOrNone
  private def resourceStorePath      = RuntimeConstants.keyStoreResource.valueOrDefault("/tls13/server.p12")
  private def fileTrustStorePath     = RuntimeConstants.trustStoreFile.propOrNone
  private def resourceTrustStorePath = RuntimeConstants.trustStoreResource.valueOrDefault("/tls13/trustedCerts.jks")

  private val keyStoreInputStream: Callable[InputStream] = openInputStream(fileStorePath, resourceStorePath)

  private def openInputStream(file: Option[String], resource: String): Callable[InputStream] = () =>
    file match {
      case Some(path) =>
        new FileInputStream(path)
      case None =>
        getClass.getResourceAsStream(resource)
    }

  private def trustStoreInputStream: Callable[InputStream] = openInputStream(fileTrustStorePath, resourceTrustStorePath)

  def fromSystemProps: TlsOpts = TlsOpts(keyStoreInputStream, keystorePassword, keyPassword, trustStoreInputStream, trustPassword, protocol)
}

final case class TlsOpts(
    keyStoreInputStream: Callable[InputStream],
    keystorePassword: String,
    keyPassword: String,
    trustStoreInputStream: Callable[InputStream],
    trustPassword: String,
    protocol: String = "TLSv1.3"
)
