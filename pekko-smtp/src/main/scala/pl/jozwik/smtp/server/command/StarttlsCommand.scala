package pl.jozwik.smtp.server.command

import pl.jozwik.smtp.server.{ MailAccumulator, ResponseMessage }
import pl.jozwik.smtp.util.SmtpResponses.TLS_NOT_SUPPORTED_RESPONSE

object StarttlsCommand {

  def handleStarttls: (MailAccumulator, ResponseMessage) =
    response(MailAccumulator.withHello, TLS_NOT_SUPPORTED_RESPONSE)

}
