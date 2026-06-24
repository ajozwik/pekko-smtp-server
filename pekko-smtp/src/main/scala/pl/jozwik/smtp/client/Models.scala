package pl.jozwik.smtp
package client

import org.apache.pekko.actor.ActorRef
import pl.jozwik.smtp.util.{ Mail, SocketAddress }

final case class MailWithAddress(mail: Mail, address: SocketAddress)

final case class Counter(senderRef: ActorRef, result: Result)

final case class ValidateError(senderRef: ActorRef, message: String)

sealed trait Result

case object SuccessResult extends Result

object FailedResult {

  def empty: FailedResult =
    FailedResult("")

}

final case class FailedResult(error: String) extends Result
