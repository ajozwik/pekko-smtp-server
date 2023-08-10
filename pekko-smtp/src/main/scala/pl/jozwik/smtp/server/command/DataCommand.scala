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

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.Errors._
import pl.jozwik.smtp.util.Utils._
import pl.jozwik.smtp.util.{ Constants, Mail, SizeParameterHandler }

object DataCommand extends StrictLogging {

  def handleData(accumulator: MailAccumulator): (MailAccumulator, ResponseMessage) = {
    val (newAcc, message) = responseForData(accumulator)
    response(newAcc, message)
  }

  def readContent(line: String, stripped: String, accumulator: MailAccumulator,
    sizeHandler: SizeParameterHandler,
    consumer: Mail => Unit): (MailAccumulator, ResponseMessage) =
    if (isFinished(stripped)) {
      sendToHandler(accumulator, consumer)
    } else {
      if (accumulator.content.size + line.length > sizeHandler.size) {
        response(accumulator, insufficientSystemStorage(sizeHandler.size))
      } else {
        (accumulator.addLine(line), NoDataResponse)
      }
    }

  private def responseForData(accumulator: MailAccumulator): (MailAccumulator, String) =
    (accumulator.from.isEmpty, accumulator.to.isEmpty) match {
      case (EMPTY, _) =>
        (accumulator, MAIL_MISSING)
      case (NOT_EMPTY, EMPTY) =>
        (accumulator, RCPT_MISSING)
      case _ =>
        (accumulator.copy(readData = READ_DATA), START_INPUT)
    }

  private def sendToHandler(accumulator: MailAccumulator, consumer: Mail => Unit) = {
    val emailContent = extractMessage(accumulator.content.content)
    val mail = Mail(accumulator.from, accumulator.to, emailContent)
    logger.debug(s"Send to handler $mail")
    consumer(mail)
    (MailAccumulator.empty, NoResponse)
  }

  private def isFinished(line: String): Boolean =
    line == Constants.END_DATA

}