package pl.jozwik.smtp.server.command

import pl.jozwik.smtp.server.{MailAccumulator, ResponseMessage}
import pl.jozwik.smtp.util.SmtpResponses.{TLS_NOT_SUPPORTED_RESPONSE, TLS_SUPPORTED_RESPONSE}

object StarttlsCommand {

  def handleStarttls(starttlsSupport: Boolean)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    if (starttlsSupport) {
      response(TLS_SUPPORTED_RESPONSE)(MailAccumulator.empty.copy(tls = true))
    } else {
      response(TLS_NOT_SUPPORTED_RESPONSE)
    }

}
