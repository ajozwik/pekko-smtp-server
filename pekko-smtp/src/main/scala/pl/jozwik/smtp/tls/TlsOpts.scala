package pl.jozwik.smtp.tls

import pl.jozwik.smtp.util.{RuntimeConstant, RuntimeConstants}

import java.io.{FileInputStream, InputStream}
import java.util.concurrent.Callable

object TlsOpts {
  val clientKeystorePassword: String = RuntimeConstants.clientKeyStorePassword.valueOrDefault("clientpass")
  val keystorePassword: String       = RuntimeConstants.keyStorePassword.valueOrDefault("changeit")
  val keyPassword: String            = keystorePassword
  val trustPassword: String          = RuntimeConstants.trustStorePassword.valueOrDefault("truststore")

  private def protocol: String = RuntimeConstants.tlsProtocol.valueOrDefault("TLSv1.3")

  // No bundled fallback on purpose: TLS material must be supplied from outside the jar,
  // either as a file path or as a classpath resource, via -Dsmtp.tls.*.
  private def keyStoreInputStream: Callable[InputStream] =
    openInputStream(RuntimeConstants.keyStoreFile, RuntimeConstants.keyStoreResource)

  private def trustStoreInputStream: Callable[InputStream] =
    openInputStream(RuntimeConstants.trustStoreFile, RuntimeConstants.trustStoreResource)

  private def openInputStream(fileConstant: RuntimeConstant, resourceConstant: RuntimeConstant): Callable[InputStream] = () =>
    (fileConstant.propOrNone, resourceConstant.propOrNone) match {
      case (Some(path), _) =>
        new FileInputStream(path)
      case (None, Some(resource)) =>
        Option(getClass.getResourceAsStream(resource)).getOrElse(
          throw new IllegalStateException(s"TLS resource not found on classpath: $resource (set by -D${resourceConstant.name})")
        )
      case (None, None) =>
        throw new IllegalStateException(
          s"No TLS material configured. Set -D${fileConstant.name}=<path> or -D${resourceConstant.name}=<classpath resource>."
        )
    }

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
