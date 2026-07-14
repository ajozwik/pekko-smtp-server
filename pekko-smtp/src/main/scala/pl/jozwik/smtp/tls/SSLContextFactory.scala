package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.TlsOpts

import java.io.InputStream
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManager, KeyManagerFactory, SSLContext, SSLEngine, TrustManager, TrustManagerFactory }
import scala.util.Using

object SSLContextFactory extends StrictLogging {

  def sslEngine(protocol: String = "TLSv1.3")(
      keyStoreInputStream: => InputStream = getClass.getResourceAsStream("/tls13/server.p12"),
      keystorePassword: String = TlsOpts.keystorePassword,
      keyPassword: String = TlsOpts.keystorePassword
  )(
      trustStoreInputStream: => InputStream = getClass.getResourceAsStream("/tls13/trustedCerts.jks"),
      trustPassword: String = TlsOpts.trustPassword
  ): () => SSLEngine =
    () =>
      try {
        val sslContext =
          createContext(protocol)(keyStoreInputStream, keystorePassword, keyPassword)(
            trustStoreInputStream,
            trustPassword
          )
        sslContext.createSSLEngine()
      } catch {
        case e: Exception =>
          logger.error(s"Error creating SSLEngine", e)
          throw e
      }

  def createContext(
      protocol: String
  )(ksInputStream: => InputStream, keystorePassword: String, keyPassword: String)(tsInputStream: => InputStream, trustPassword: String): SSLContext = {
    val c            = SSLContext.getInstance(protocol)
    val keyManager   = createKeyManagers("PKCS12")(ksInputStream, keystorePassword, keyPassword)
    val trustManager = createTrustManagers("JKS")(tsInputStream, trustPassword)
    c.init(
      keyManager,
      trustManager,
      new SecureRandom()
    )
    c
  }

  private def createKeyManagers(algorithm: String)(source: => InputStream, keystorePassword: String, keyPassword: String): Array[KeyManager] = {
    val keyStore = readCertificate(algorithm)(source, keystorePassword)
    val kmf      = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, keyPassword.toCharArray)
    kmf.getKeyManagers
  }

  private def createTrustManagers(algorithm: String)(source: => InputStream, keystorePassword: String): Array[TrustManager] = {
    val trustStore   = readCertificate(algorithm)(source, keystorePassword)
    val trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustFactory.init(trustStore)
    trustFactory.getTrustManagers
  }

  private def readCertificate(algorithm: String)(source: => InputStream, keystorePassword: String): KeyStore = {
    val keyStore = KeyStore.getInstance(algorithm)
    Using.resource(source) { keyStoreIS =>
      keyStore.load(keyStoreIS, keystorePassword.toCharArray)
    }
    keyStore
  }

}
