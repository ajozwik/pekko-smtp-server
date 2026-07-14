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

  private def handleVrfy(implicit acc: MailAccumulator) =
    response(CANNOT_VERIFY)

  private def handleReset(implicit acc: MailAccumulator) =
    response(RESET_OK)(MailAccumulator(acc.needHello))

  private def handleNoop(line: String)(implicit acc: MailAccumulator) = {
    val message = if (line.trim == NOOP) {
      NOOP_OK
    } else {
      commandNotRecognized(line)
    }
    response(message)
  }

  private def handleQuit(localHostName: String)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    closeResponse(closingChannel(localHostName))(acc.emptyLeaveTls)

}

final case class MessageHandler(
    addressHandler: AddressHandler,
    sizeHandler: SizeParameterHandler,
    localHostName: String,
    remote: InetSocketAddress,
    consumer: Mail => Unit
) {

  import MessageHandler.*

  def handleMessage(line: String, stripped: String, starttlsSupport: Boolean)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    if (acc.readData) {
      DataCommand.readContent(line, stripped, sizeHandler, consumer)
    } else {
      val (command, argument) = splitLineByColon(stripped)
      val commandIterator     = splitOnWhiteSpaces(command).map(_.toUpperCase(Constants.LocaleRoot))
      handleCommandMessage(command, stripped, commandIterator.iterator, argument, starttlsSupport)
    }

  private def handleCommandMessage(
      command: String,
      line: String,
      commandIterator: Iterator[String],
      argument: String,
      starttlsSupport: Boolean
  )(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    commandIterator.next() match {
      case HELO =>
        HelloCommand.handleHelo(localHostName, remote)
      case EHLO =>
        HelloCommand.handleEhlo(localHostName, remote, sizeHandler.size)
      case DATA =>
        DataCommand.handleData
      case MAIL =>
        MailCommand.handleMail(command, commandIterator, argument, sizeHandler, addressHandler)
      case RCPT =>
        RcptCommand.handleRcpt(commandIterator, argument, addressHandler)
      case STARTTLS =>
        StarttlsCommand.handleStarttls(starttlsSupport)
      case HANDSHAKE =>
        HandshakeCommand.handleHandshake
      case QUIT =>
        handleQuit(localHostName)
      case RSET =>
        handleReset
      case NOOP =>
        handleNoop(line)
      case VRFY =>
        handleVrfy
      case _ =>
        commandNotImplemented(line)
    }

  private def commandNotImplemented(line: String)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    response(commandNotRecognized(line))

}
