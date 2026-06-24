package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.util.ScalaApp

object Main extends ScalaApp {
  private val serverOpts                   = ServerOpts.fromSystemProps
  private implicit val system: ActorSystem = ActorSystem(s"SMTP-${serverOpts.port}")
  private val r                            = new Run((host, port) => Tcp().bind(host, port))(serverOpts)
  r.server

}
