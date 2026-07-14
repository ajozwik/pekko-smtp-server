package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.TlsOpts
import pl.jozwik.smtp.server.ServerOpts
import pl.jozwik.smtp.util.ScalaAppWithLogger

object Main extends ScalaAppWithLogger {
  private val serverOpts                   = ServerOpts.fromSystemProps
  private implicit val system: ActorSystem = ActorSystem(s"SMTP-${serverOpts.port}")

  scala.sys.addShutdownHook {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    system.terminate().foreach { case _ =>
      logger.warn("Terminated")
    }

  }

  private[runtime] lazy val r = new Run((host, port) => Tcp().bind(host, port))(serverOpts, Option(TlsOpts.fromSystemProps))
  r.start()

}
