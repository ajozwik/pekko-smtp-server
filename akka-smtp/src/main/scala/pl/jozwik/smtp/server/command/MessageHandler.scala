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

import java.net.InetSocketAddress

import pl.jozwik.smtp.server.Errors._
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.Response._
import pl.jozwik.smtp.util.Utils._
import pl.jozwik.smtp.util.{ Mail, SizeParameterHandler }

object MessageHandler {
  private def handleVrfy(accumulator: MailAccumulator) =
    response(accumulator, CANNOT_VERIFY)

  private def handleReset(accumulator: MailAccumulator) =
    response(MailAccumulator(accumulator.needHello), RESET_OK)

  private def handleNoop(line: String, accumulator: MailAccumulator) = {
    val message = if (line.trim == NOOP) {
      NOOP_OK
    } else {
      commandNotRecognized(line)
    }
    response(accumulator, message)

  }

  private def handleQuit(localHostName: String): (MailAccumulator, ResponseMessage) =
    closeResponse(MailAccumulator.empty, closingChannel(localHostName))

}

final case class MessageHandler(addressHandler: AddressHandler, sizeHandler: SizeParameterHandler, localHostName: String,
    remote: InetSocketAddress, consumer: Mail => Unit) {

  import MessageHandler._

  def handleMessage(
    line: String,
    stripped: String,
    accumulator: MailAccumulator): (MailAccumulator, ResponseMessage) =
    if (accumulator.readData) {
      DataCommand.readContent(line, stripped, accumulator, sizeHandler, consumer)
    } else {
      val (command, argument) = splitLineByColon(stripped)
      val commandIterator = splitOnWhiteSpaces(command).map(_.toUpperCase)
      handleCommandMessage(command, stripped, commandIterator.iterator, argument, accumulator)
    }

  private def handleCommandMessage(
    command: String,
    line: String, commandIterator: Iterator[String],
    argument: String,
    accumulator: MailAccumulator): (MailAccumulator, ResponseMessage) =
    commandIterator.next() match {
      case HELO =>
        HelloCommand.handleHelo(localHostName, remote)
      case EHLO =>
        HelloCommand.handleEhlo(localHostName, remote, sizeHandler.size)
      case DATA =>
        DataCommand.handleData(accumulator)
      case MAIL =>
        MailCommand.handleMail(command, commandIterator, argument, accumulator, sizeHandler, addressHandler)
      case RCPT =>
        RcptCommand.handleRcpt(commandIterator, argument, accumulator, addressHandler)
      case STARTTLS =>
        StarttlsCommand.handleStarttls
      case other: String =>
        handleOtherMessages(other, line, accumulator)
    }

  private def handleOtherMessages(command: String, line: String, accumulator: MailAccumulator) = command match {
    case QUIT =>
      handleQuit(localHostName)
    case RSET =>
      handleReset(accumulator)
    case NOOP =>
      handleNoop(line, accumulator)
    case VRFY =>
      handleVrfy(accumulator)
    case _ =>
      commandNotImplemented(line, accumulator)
  }

  private def commandNotImplemented(line: String, accumulator: MailAccumulator): (MailAccumulator, ResponseMessage) = {
    response(accumulator, commandNotRecognized(line))

  }

}