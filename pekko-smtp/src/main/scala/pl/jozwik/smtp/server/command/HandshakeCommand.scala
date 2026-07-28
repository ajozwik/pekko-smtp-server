package pl.jozwik.smtp.server.command

import pl.jozwik.smtp.server.{MailAccumulator, ResponseMessage}
import pl.jozwik.smtp.util.SmtpResponses

object HandshakeCommand {

  def handleHandshake(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    response(SmtpResponses.HANDSHAKE_RESPONSE)(acc.emptyLeaveTls)

}
