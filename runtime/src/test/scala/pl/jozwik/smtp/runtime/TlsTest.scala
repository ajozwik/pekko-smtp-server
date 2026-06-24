package pl.jozwik.smtp.runtime

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.ActorSpec
import pl.jozwik.smtp.runtime.tls.NoOpX509TrustManager
import pl.jozwik.smtp.server.consumer.LogConsumer
import pl.jozwik.smtp.server.{ ConnectionHandler, NopAddressHandler, StreamServer }
import pl.jozwik.smtp.util.ScalaAppWithLogger

import java.security.SecureRandom
import javax.net.ssl.SSLContext
import scala.concurrent.duration.DurationInt

object TlsTest extends ScalaAppWithLogger {
  protected implicit val actorSystem: ActorSystem = ActorSystem(s"test-${ActorSpec.number.next()}", ConfigFactory.parseResources("application-test.conf"))

  private val sslEngine = () => {
    val sslContext   = SSLContext.getInstance("TLS")
    val trustManager = new NoOpX509TrustManager
    sslContext.init(
      null,
      Array(trustManager),
      new SecureRandom()
    )
    val engine = sslContext.createSSLEngine()
    engine.setUseClientMode(false)
    engine
  }


  val streamServer = StreamServer((host, port) => Tcp().bindWithTls(host, port, sslEngine), 8443)(
    ConnectionHandler.connectionHandler(NopAddressHandler, 1024, LogConsumer.consumer, 2.minutes)
  )

}
