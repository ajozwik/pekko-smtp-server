package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.server.ServerOpts
import pl.jozwik.smtp.server.StreamServerRunner
import pl.jozwik.smtp.tls.{EphemeralTls, TlsOpts}
import pl.jozwik.smtp.util.{RuntimeConstants, ScalaAppWithLogger}

object Main extends ScalaAppWithLogger {
  private val serverOpts                   = ServerOpts.fromSystemProps
  private implicit val system: ActorSystem = ActorSystem(s"SMTP-${serverOpts.port}")

  // Opt-in escape hatch for demos/local runs: -Dsmtp.tls.ephemeral=true skips the requirement
  // for a real keystore/truststore by generating a throwaway self-signed one instead.
  private val tlsOpts: TlsOpts =
    if (RuntimeConstants.ephemeral.valueOrDefault(false).toBoolean) EphemeralTls.serverTlsOpts else TlsOpts.fromSystemProps

  scala.sys.addShutdownHook {
    implicit val ec: scala.concurrent.ExecutionContext = system.dispatcher
    system.terminate().foreach(_ => logger.warn("Terminated"))

  }

  private[runtime] lazy val r = new StreamServerRunner((host, port) => Tcp().bind(host, port))(serverOpts, "server", Option(tlsOpts))
  r.start()

}
