package pl.jozwik.smtp

import org.apache.pekko.actor.Props
import pl.jozwik.smtp.server.ActorWithTimeout

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object UnhandledActor {
  def props: Props    = Props[UnhandledActor]()
  val TimeoutResponse = "Timeout"
}

class UnhandledActor extends ActorWithTimeout {

  def receive: Receive = { case _: String =>
    logger.debug("Consume")
  }

  override def timeout: FiniteDuration = FiniteDuration(1, TimeUnit.MILLISECONDS)

  override protected def sendTimeoutMessage(lastAccess: LocalDateTime): Unit =
    sender() ! UnhandledActor.TimeoutResponse

}
