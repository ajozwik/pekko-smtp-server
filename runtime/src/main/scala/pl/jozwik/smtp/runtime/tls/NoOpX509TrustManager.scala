package pl.jozwik.smtp.runtime.tls

import com.typesafe.scalalogging.StrictLogging

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class NoOpX509TrustManager extends X509TrustManager with StrictLogging {

  def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit =
    logger.debug(s"checkClientTrusted ${chain.toSeq} $authType")

  def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit =
    logger.debug(s"checkServerTrusted ${chain.toSeq} $authType")

  def getAcceptedIssuers: Array[X509Certificate] =
    Array.empty[X509Certificate]

}
