package pl.jozwik.smtp.actor

import akka.actor.{ Actor, ActorLogging }
import com.typesafe.scalalogging.StrictLogging

trait AbstractActor extends Actor with StrictLogging with ActorLogging {
  protected final val DISCARD = true

  override def preStart(): Unit = {
    logger.debug(s"$self Actor preStart")
    super.preStart()
  }

  override def postStop(): Unit = {
    logger.debug(s"$self Actor postStop")
    super.postStop()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.debug(s"$self preRestart $message", reason)
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    logger.debug(s"$self postRestart ${reason.getMessage}")
    super.postRestart(reason)
  }

  override def unhandled(message: Any): Unit = {
    logger.error(s"Unhandled message in $self $message from ${sender()}")
    super.unhandled(message)
  }

  protected def become(state: Receive): Unit = {
    context.become(state, DISCARD)
  }
}
