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
      addressHandler: AddressHandler
  )(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) = {
    val (newAcc, message) = if (iterator.hasNext && iterator.next() == TO && !iterator.hasNext) {
      responseForRcptAndValidation(argument, addressHandler)
    } else {
      (acc, syntaxError(TO))
    }
    response(message)(newAcc)
  }

  private def responseForRcptAndValidation(
      argument: String,
      addressHandler: AddressHandler
  )(implicit acc: MailAccumulator): (MailAccumulator, String) =
    (acc.from.isEmpty, argument.isEmpty) match {
      case (EMPTY, _) =>
        (acc, MAIL_MISSING)
      case (NOT_EMPTY, EMPTY) =>
        (acc, syntaxError(s"$TO"))
      case _ =>
        responseForRcptTo(argument, addressHandler)

    }

  private def responseForRcptTo(argument: String, addressHandler: AddressHandler)(implicit acc: MailAccumulator): (MailAccumulator, String) =
    toMailAddress(argument) match {
      case Right(mailAddress) if addressHandler.acceptTo(mailAddress) =>
        val newAcc = acc.copy(to = mailAddress +: acc.to)
        (newAcc, recipientOk(mailAddress))
      case Right(mailAddress) =>
        (acc, userUnknown(mailAddress))
      case Left(error) =>
        (acc, error)
    }

}
