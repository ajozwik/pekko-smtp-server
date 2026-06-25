package pl.jozwik.smtp.server.command

import pl.jozwik.smtp.server.{ MailAccumulator, ResponseMessage }
import pl.jozwik.smtp.util.SmtpResponses.TLS_SUPPORTED_RESPONSE

object StarttlsCommand {

  def handleStarttls: (MailAccumulator, ResponseMessage) =
    response(MailAccumulator.empty.copy(tls = true), TLS_SUPPORTED_RESPONSE)

}
