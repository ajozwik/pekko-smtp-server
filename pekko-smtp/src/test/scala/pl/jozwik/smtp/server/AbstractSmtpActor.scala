package pl.jozwik.smtp.server

import java.net.InetSocketAddress

import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.io.Tcp.{ Bind, Bound, CommandFailed, Unbind }
import org.apache.pekko.io.{ IO, Tcp }
import pl.jozwik.smtp.AbstractActor

abstract class AbstractSmtpActor(bindAddress: InetSocketAddress) extends AbstractActor {
  import context.system

  override def unhandled(message: Any): Unit = message match {
    case b @ Bound(_) =>
      logger.debug(s"$b")
    case CommandFailed(c) =>
      logger.error(s"$c\n${c.failureMessage}")
      self ! PoisonPill
    case _ =>
      super.unhandled(message)
  }

  override def preStart(): Unit = {
    IO(Tcp) ! Bind(self, bindAddress)
    super.preStart()
  }

  override def postStop(): Unit = {
    IO(Tcp) ! Unbind
    super.postStop()
  }

}
