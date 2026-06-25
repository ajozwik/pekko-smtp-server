package pl.jozwik.smtp
package server
package command

import java.net.InetSocketAddress
import pl.jozwik.smtp.server.Errors.*
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.Response.*
import pl.jozwik.smtp.util.SmtpResponses.*
import pl.jozwik.smtp.util.Utils.*
import pl.jozwik.smtp.util.{ Constants, Mail, SizeParameterHandler }

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

final case class MessageHandler(
    addressHandler: AddressHandler,
    sizeHandler: SizeParameterHandler,
    localHostName: String,
    remote: InetSocketAddress,
    consumer: Mail => Unit
) {

  import MessageHandler.*

  def handleMessage(line: String, stripped: String, accumulator: MailAccumulator): (MailAccumulator, ResponseMessage) =
    if (accumulator.readData) {
      DataCommand.readContent(line, stripped, accumulator, sizeHandler, consumer)
    } else {
      val (command, argument) = splitLineByColon(stripped)
      val commandIterator     = splitOnWhiteSpaces(command).map(_.toUpperCase(Constants.LocaleRoot))
      handleCommandMessage(command, stripped, commandIterator.iterator, argument, accumulator)
    }

  private def handleCommandMessage(
      command: String,
      line: String,
      commandIterator: Iterator[String],
      argument: String,
      accumulator: MailAccumulator
  ): (MailAccumulator, ResponseMessage) =
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
