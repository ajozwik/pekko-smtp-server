package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.tls.SSLContextFactory
import pl.jozwik.smtp.util.ScalaAppWithLogger

import java.io.File

object TlsTest extends ScalaAppWithLogger {
  logger.debug(s"Starting TLS test ${new File("").getAbsolutePath}")

  private val serverOpts                   = ServerOpts.fromSystemProps
  private implicit val system: ActorSystem = ActorSystem(s"SMTP-${serverOpts.port}")
  private val r                            = new Run((host, port) => Tcp().bindWithTls(host, port, SSLContextFactory.sslEngine()()))(serverOpts)
  r.server

}
