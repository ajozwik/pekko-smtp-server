package pl.jozwik.smtp.tls

import java.io.{FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.util.Using

object SSLContextFactory {

  def createServerSSLContextFromPKCS12(
      keyStoreInputStream: => InputStream = new FileInputStream("server.p12"),
      keystorePassword: String = "changeit",
      keyPassword: String = "changeit"
  )(trustStoreInputStream: => InputStream = new FileInputStream("truststore.jks"), trustPassword: String = "changeit"): SSLContext = {
    val keyStore = KeyStore.getInstance("PKCS12")
    Using.resource(keyStoreInputStream) { is =>
      keyStore.load(is, keystorePassword.toCharArray)
    }

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keyStore, keyPassword.toCharArray)

    // Optional: Create truststore for mutual TLS
    val trustStore = KeyStore.getInstance("JKS")
    Using.resource(trustStoreInputStream) { is =>
      trustStore.load(is, trustPassword.toCharArray)
    }

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(trustStore)

    val sslContext = SSLContext.getInstance("TLSv1.3")
    sslContext.init(
      keyManagerFactory.getKeyManagers,
      trustManagerFactory.getTrustManagers,
      new SecureRandom()
    )

    sslContext
  }

}
