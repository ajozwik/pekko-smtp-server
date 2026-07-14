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
      sizeHandler: SizeParameterHandler,
      addressHandler: AddressHandler
  )(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    acc.needHello match {
      case true =>
        response(HELLO_FIRST)
      case _ if commandIterator.hasNext && commandIterator.next() == FROM && !commandIterator.hasNext =>
        addMail(argument, sizeHandler, addressHandler)
      case _ =>
        response(syntaxError(command))
    }

  private def addMail(
      argument: String,
      sizeHandler: SizeParameterHandler,
      addressHandler: AddressHandler
  )(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    if (acc.from.isEmpty) {
      val parameterHandlerMap = Map(sizeHandler.key -> sizeHandler)
      addMailFrom(argument, parameterHandlerMap, addressHandler)
    } else {
      response(SENDER_ALREADY_SPECIFIED)
    }

  private def addMailFrom(
      argument: String,

      parameterHandlerMap: Map[String, ParameterHandler],
      addressHandler: AddressHandler
  )(implicit acc: MailAccumulator) = {
    val (message, from, parameters) = responseForMail(argument, addressHandler)
    Parameters.validate(parameters, parameterHandlerMap) match {
      case Left(error) =>
        response(error)
      case _ =>
        response(message)(acc.copy(from = from))
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

  private def extractSender(argument: String, addressHandler: AddressHandler, address: String): (String, MailAddress) = toMailAddress(
    address
  ) match {
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
