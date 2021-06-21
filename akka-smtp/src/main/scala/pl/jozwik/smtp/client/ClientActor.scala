package pl.jozwik.smtp.client

import akka.actor.Props
import pl.jozwik.smtp.actor.AbstractActor

import scala.concurrent.duration._

object ClientActor {
  import scala.language.postfixOps
  def props(timeout: FiniteDuration = 2 minutes): Props = Props(new ClientActor(timeout))
}

class ClientActor(timeout: FiniteDuration) extends AbstractActor {

  def handleMessage(success: Int, failed: Int): Receive = {
    case MailWithAddress(mail, address) =>
      context.actorOf(ClientActorHandler.props(sender(), address, mail, timeout))
      ()

    case Counter(senderRef, result) =>
      result match {
        case SuccessResult =>
          logAndBecome(success + 1, failed)
        case FailedResult(_) =>
          logAndBecome(success, failed + 1)
      }
      senderRef ! result
  }

  override def receive: Receive = handleMessage(0, 0)

  private def logAndBecome(success: Int, failed: Int): Unit = {
    logger.debug(s"END: success:$success failed:$failed")
    become(handleMessage(success, failed))
  }
}
