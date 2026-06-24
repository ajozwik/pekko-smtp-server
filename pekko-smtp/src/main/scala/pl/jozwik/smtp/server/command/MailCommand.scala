package pl.jozwik.smtp
package server
package command

import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.Response.*
import pl.jozwik.smtp.util.Utils.*
import pl.jozwik.smtp.util.{ MailAddress, ParameterHandler, Parameters, SizeParameterHandler }

object MailCommand {

  import Errors.*

  def handleMail(
      command: String,
      commandIterator: Iterator[String],
      argument: String,
      accumulator: MailAccumulator,
      sizeHandler: SizeParameterHandler,
      addressHandler: AddressHandler
  ): (MailAccumulator, ResponseMessage) =
    accumulator.needHello match {
      case true =>
        response(accumulator, HELLO_FIRST)
      case _ if commandIterator.hasNext && commandIterator.next() == FROM && !commandIterator.hasNext =>
        addMail(argument, accumulator, sizeHandler, addressHandler)
      case _ =>
        response(accumulator, syntaxError(command))
    }

  private def addMail(
      argument: String,
      accumulator: MailAccumulator,
      sizeHandler: SizeParameterHandler,
      addressHandler: AddressHandler
  ): (MailAccumulator, ResponseMessage) =
    if (accumulator.from.isEmpty) {
      val parameterHandlerMap = Map(sizeHandler.key -> sizeHandler)
      addMailFrom(argument, accumulator, parameterHandlerMap, addressHandler)
    } else {
      response(accumulator, SENDER_ALREADY_SPECIFIED)
    }

  private def addMailFrom(
      argument: String,
      accumulator: MailAccumulator,
      parameterHandlerMap: Map[String, ParameterHandler],
      addressHandler: AddressHandler
  ) = {
    val (message, from, parameters) = responseForMail(argument, addressHandler)
    Parameters.validate(parameters, parameterHandlerMap) match {
      case Left(error) =>
        response(accumulator, error)
      case _ =>
        response(accumulator.copy(from = from), message)
    }
  }

  private def responseForMail(argument: String, addressHandler: AddressHandler): (String, MailAddress, Seq[(String, String)]) =
    extractAddressAndParameters(argument) match {
      case Right((address, map)) =>
        val (response, mailAddress) = extractSender(argument, addressHandler, address)
        (response, mailAddress, map.toSeq)
      case Left(response) =>
        (response, MailAddress.empty, Seq.empty)
    }

  private def extractSender(argument: String, addressHandler: AddressHandler, address: String): (String, MailAddress) = toMailAddress(address) match {
    case Right(from) =>
      val response = if (addressHandler.acceptFrom(from)) {
        senderOk(from)
      } else {
        domainNotResolved(from)
      }
      (response, from)
    case Left(_) =>
      val response = if (argument.isEmpty) {
        syntaxError(FROM)
      } else {
        domainNameRequired(argument)
      }
      (response, MailAddress.empty)
  }

}
