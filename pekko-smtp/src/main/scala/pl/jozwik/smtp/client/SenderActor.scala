package pl.jozwik.smtp.client

import org.apache.pekko.actor.Props
import pl.jozwik.smtp.actor.AbstractActor

import scala.concurrent.duration._

object SenderActor {
  def props(timeout: FiniteDuration = 2 minutes): Props = Props(new SenderActor(timeout))
}

class SenderActor(timeout: FiniteDuration) extends AbstractActor {

  def handleMessage(success: Int, failed: Int): Receive = {
    case MailWithAddress(mail, address) =>
      context.actorOf(SenderActorHandler.props(sender(), address, mail, timeout))
      ()

    case Counter(senderRef, result) =>
      result match {
        case SuccessResult =>
          logAndBecome(success + 1, failed)
        case FailedResult(_) =>
          logAndBecome(success, failed + 1)
      }
      senderRef ! result
    case ValidateError(senderRef, message) =>
      senderRef ! FailedResult(message)
  }

  override def receive: Receive = handleMessage(0, 0)

  private def logAndBecome(success: Int, failed: Int): Unit = {
    logger.debug(s"END: success:$success failed:$failed")
    become(handleMessage(success, failed))
  }

}
