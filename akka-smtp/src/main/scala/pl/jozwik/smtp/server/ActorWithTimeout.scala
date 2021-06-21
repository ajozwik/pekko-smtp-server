package pl.jozwik.smtp.server

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import akka.actor.Cancellable
import pl.jozwik.smtp.actor.AbstractActor
import pl.jozwik.smtp.server.ActorWithTimeout.TimeoutTick

import scala.concurrent.duration.FiniteDuration

object ActorWithTimeout {
  val TIMEOUT: String = "TIMEOUT"
  case object TimeoutTick
}

trait ActorWithTimeout extends AbstractActor {

  import context.dispatcher

  private var cancellable: Option[Cancellable] = None

  private var lastAccess: LocalDateTime = LocalDateTime.now()

  val timeout: FiniteDuration

  protected val tick: FiniteDuration = timeout / 2

  override def preStart(): Unit = {
    super.preStart()
    cancellable = Option(context.system.scheduler.scheduleOnce(tick, self, TimeoutTick))
    ()
  }

  override def postStop(): Unit = {
    super.postStop()
    cancellable.foreach(_.cancel())
  }

  override def unhandled(message: Any): Unit = message match {
    case TimeoutTick =>
      if (LocalDateTime.now.minus(timeout.toMillis, ChronoUnit.MILLIS).isAfter(lastAccess)) {
        sendTimeoutMessage(lastAccess)
      } else {
        cancellable = Some(context.system.scheduler.scheduleOnce(tick, self, TimeoutTick))
      }
    case _ =>
      super.unhandled(message)
  }

  protected def sendTimeoutMessage(lastAccess: LocalDateTime): Unit

  protected def resetTimeout(): Unit = {
    lastAccess = LocalDateTime.now()
  }

  protected override def become(state: Receive): Unit = {
    super.become(state)
    resetTimeout()
  }

}
