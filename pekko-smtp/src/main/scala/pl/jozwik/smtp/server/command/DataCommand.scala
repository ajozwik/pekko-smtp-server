package pl.jozwik.smtp
package server
package command

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.Errors.*
import pl.jozwik.smtp.util.Utils.*
import pl.jozwik.smtp.util.{ Constants, Mail, SizeParameterHandler }

object DataCommand extends StrictLogging {

  def handleData(accumulator: MailAccumulator): (MailAccumulator, ResponseMessage) = {
    val (newAcc, message) = responseForData(accumulator)
    response(newAcc, message)
  }

  def readContent(
      line: String,
      stripped: String,
      accumulator: MailAccumulator,
      sizeHandler: SizeParameterHandler,
      consumer: Mail => Unit
  ): (MailAccumulator, ResponseMessage) =
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
    val mail         = Mail(accumulator.from, accumulator.to, emailContent)
    logger.debug(s"Send to handler $mail")
    consumer(mail)
    (MailAccumulator.empty, NoResponse)
  }

  private def isFinished(line: String): Boolean =
    line == Constants.END_DATA

}
