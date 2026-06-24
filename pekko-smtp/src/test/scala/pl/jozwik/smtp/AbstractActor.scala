package pl.jozwik.smtp

import org.apache.pekko.actor.{ Actor, ActorLogging }
import com.typesafe.scalalogging.StrictLogging

trait AbstractActor extends Actor with StrictLogging with ActorLogging {

  val DISCARD = true

  protected def become(state: Receive, stateName: String = ""): Unit = {
    logger.debug(s"$getClass Change state to $stateName")
    context.become(state, DISCARD)
  }

  override def preStart(): Unit = {
    super.preStart()
    logger.debug(s"$getClass $self preStart")
  }

  override def postStop(): Unit = {
    super.postStop()
    logger.debug(s"$self postStop")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.debug(s"$self preRestart $message  $hashCode", reason)
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    logger.debug(s"$self postRestart ${reason.getMessage}")
    super.postRestart(reason)
  }

  override def unhandled(message: Any): Unit = {
    logger.error(s"$getClass Unhandled message in `$self` message `$message` from ${sender()}")
    super.unhandled(message)
  }

}
