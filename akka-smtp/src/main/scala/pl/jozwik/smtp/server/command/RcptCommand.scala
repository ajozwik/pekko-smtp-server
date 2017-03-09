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

import pl.jozwik.smtp.server.Errors._
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.Response._
import pl.jozwik.smtp.util.Utils._

object RcptCommand {
  def handleRcpt(iterator: Iterator[String], argument: String, accumulator: MailAccumulator,
    addressHandler: AddressHandler): (MailAccumulator, ResponseMessage) = {
    val (acc, message) = if (iterator.hasNext && iterator.next() == TO && !iterator.hasNext) {
      responseForRcptAndValidation(accumulator, argument, addressHandler)
    } else {
      (accumulator, syntaxError(TO))
    }
    response(acc, message)
  }

  private def responseForRcptAndValidation(accumulator: MailAccumulator, argument: String,
    addressHandler: AddressHandler): (MailAccumulator, String) =
    (accumulator.from.isEmpty, argument.isEmpty) match {
      case (EMPTY, _) =>
        (accumulator, MAIL_MISSING)
      case (NOT_EMPTY, EMPTY) =>
        (accumulator, syntaxError(s"$TO"))
      case _ =>
        responseForRcptTo(accumulator, argument, addressHandler)

    }

  private def responseForRcptTo(accumulator: MailAccumulator, argument: String,
    addressHandler: AddressHandler): (MailAccumulator, String) =
    toMailAddress(argument) match {
      case Right(mailAddress) if addressHandler.acceptTo(mailAddress) =>
        val acc = accumulator.copy(to = mailAddress +: accumulator.to)
        (acc, recipientOk(mailAddress))
      case Right(mailAddress) if !addressHandler.acceptTo(mailAddress) =>
        (accumulator, userUnknown(mailAddress))
      case Left(error) =>
        (accumulator, error)
    }
}