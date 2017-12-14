/*
 * Copyright (c) 2017 Andrzej Jozwik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pl.jozwik.smtp
package server
package command

import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.Response._
import pl.jozwik.smtp.util.Utils._
import pl.jozwik.smtp.util.{MailAddress, ParameterHandler, Parameters, SizeParameterHandler}

object MailCommand {

  import Errors._

  def handleMail(command: String, commandIterator: Iterator[String], argument: String, accumulator: MailAccumulator,
    sizeHandler: SizeParameterHandler,
    addressHandler: AddressHandler): (MailAccumulator, ResponseMessage) =
    accumulator.needHello match {
      case true =>
        response(accumulator, HELLO_FIRST)
      case _ if commandIterator.hasNext && commandIterator.next() == FROM && !commandIterator.hasNext =>
        addMail(argument, accumulator, sizeHandler, addressHandler)
      case _ =>
        response(accumulator, syntaxError(command))
    }

  private def addMail(argument: String, accumulator: MailAccumulator, sizeHandler: SizeParameterHandler,
    addressHandler: AddressHandler): (MailAccumulator, ResponseMessage) =
    if (accumulator.from.isEmpty) {
      val parameterHandlerMap = Map(sizeHandler.key -> sizeHandler)
      addMailFrom(argument, accumulator, parameterHandlerMap, addressHandler)
    } else {
      response(accumulator, SENDER_ALREADY_SPECIFIED)
    }

  private def addMailFrom(argument: String, accumulator: MailAccumulator,
    parameterHandlerMap: Map[String, ParameterHandler],
    addressHandler: AddressHandler) = {
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

  private def extractSender(
    argument: String,
    addressHandler: AddressHandler,
    address: String): (String, MailAddress) = toMailAddress(address) match {
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