package pl.jozwik.smtp.server

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.apache.pekko.actor.Cancellable
import pl.jozwik.smtp.actor.AbstractActor
import pl.jozwik.smtp.server.ActorWithTimeout.TimeoutTick

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration

object ActorWithTimeout {
  val TIMEOUT: String = "TIMEOUT"
  case object TimeoutTick
}

trait ActorWithTimeout extends AbstractActor {

  import context.dispatcher

  private val cancellable = new AtomicReference[Seq[Cancellable]](Seq.empty)

  private val lastAccess: AtomicReference[LocalDateTime] = new AtomicReference(LocalDateTime.now())

  val timeout: FiniteDuration

  private val tick: FiniteDuration = timeout / 2

  override def preStart(): Unit = {
    super.preStart()
    addCancellable()
    ()
  }



  override def postStop(): Unit = {
    super.postStop()
    cancellable.get().foreach(_.cancel())
  }

  override def unhandled(message: Any): Unit = message match {
    case TimeoutTick =>
      if (LocalDateTime.now.minus(timeout.toMillis, ChronoUnit.MILLIS).isAfter(lastAccess.get())) {
        sendTimeoutMessage(lastAccess.get())
      } else {
        addCancellable()
      }
    case _ =>
      super.unhandled(message)
  }

  protected def sendTimeoutMessage(lastAccess: LocalDateTime): Unit

  private def addCancellable() = {
    val c = context.system.scheduler.scheduleOnce(tick, self, TimeoutTick)
    cancellable.getAndAccumulate(Seq(c), (prev, next) => prev.filterNot(_.isCancelled) ++ next)
  }

  private def resetTimeout(): Unit =
    lastAccess.set(LocalDateTime.now())

  protected override def become(state: Receive): Unit = {
    super.become(state)
    resetTimeout()
  }

}
