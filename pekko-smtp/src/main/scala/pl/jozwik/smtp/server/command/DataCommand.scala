package pl.jozwik.smtp
package server
package command

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.Errors.*
import pl.jozwik.smtp.util.Utils.*
import pl.jozwik.smtp.util.{ Constants, Mail, SizeParameterHandler }

object DataCommand extends StrictLogging {

  def handleData(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) = {
    val (newAcc, message) = responseForData
    response(message)(newAcc)
  }

  def readContent(
      line: String,
      stripped: String,
      sizeHandler: SizeParameterHandler,
      consumer: Mail => Unit
  )(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    if (isFinished(stripped)) {
      sendToHandler(acc, consumer)
    } else {
      if (acc.content.size + line.length > sizeHandler.size) {
        response(insufficientSystemStorage(sizeHandler.size))
      } else {
        (acc.addLine(line), NoDataResponse)
      }
    }

  private def responseForData(implicit acc: MailAccumulator): (MailAccumulator, String) =
    (acc.from.isEmpty, acc.to.isEmpty) match {
      case (EMPTY, _) =>
        (acc, MAIL_MISSING)
      case (NOT_EMPTY, EMPTY) =>
        (acc, RCPT_MISSING)
      case _ =>
        (acc.copy(readData = READ_DATA), START_INPUT)
    }

  private def sendToHandler(acc: MailAccumulator, consumer: Mail => Unit) = {
    val emailContent = extractMessage(acc.content.content)
    val mail         = Mail(acc.from, acc.to, emailContent)
    logger.trace(s"Send to handler $mail")
    consumer(mail)
    (acc.emptyLeaveTls, NoResponse)
  }

  private def isFinished(line: String): Boolean =
    line == Constants.END_DATA

}
