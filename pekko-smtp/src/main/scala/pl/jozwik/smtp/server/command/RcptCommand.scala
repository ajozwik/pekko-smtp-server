package pl.jozwik.smtp
package server
package command

import pl.jozwik.smtp.server.Errors.*
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.Response.*
import pl.jozwik.smtp.util.Utils.*

object RcptCommand {

  def handleRcpt(
      iterator: Iterator[String],
      argument: String,
      accumulator: MailAccumulator,
      addressHandler: AddressHandler
  ): (MailAccumulator, ResponseMessage) = {
    val (acc, message) = if (iterator.hasNext && iterator.next() == TO && !iterator.hasNext) {
      responseForRcptAndValidation(accumulator, argument, addressHandler)
    } else {
      (accumulator, syntaxError(TO))
    }
    response(acc, message)
  }

  private def responseForRcptAndValidation(accumulator: MailAccumulator, argument: String, addressHandler: AddressHandler): (MailAccumulator, String) =
    (accumulator.from.isEmpty, argument.isEmpty) match {
      case (EMPTY, _) =>
        (accumulator, MAIL_MISSING)
      case (NOT_EMPTY, EMPTY) =>
        (accumulator, syntaxError(s"$TO"))
      case _ =>
        responseForRcptTo(accumulator, argument, addressHandler)

    }

  private def responseForRcptTo(accumulator: MailAccumulator, argument: String, addressHandler: AddressHandler): (MailAccumulator, String) =
    toMailAddress(argument) match {
      case Right(mailAddress) if addressHandler.acceptTo(mailAddress) =>
        val acc = accumulator.copy(to = mailAddress +: accumulator.to)
        (acc, recipientOk(mailAddress))
      case Right(mailAddress) =>
        (accumulator, userUnknown(mailAddress))
      case Left(error) =>
        (accumulator, error)
    }

}
