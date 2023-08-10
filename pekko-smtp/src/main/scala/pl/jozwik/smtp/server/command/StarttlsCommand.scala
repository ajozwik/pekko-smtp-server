package pl.jozwik.smtp.server.command

import pl.jozwik.smtp.server.{ MailAccumulator, ResponseMessage }
import pl.jozwik.smtp.util.Constants.TLS_NOT_SUPPORTED

object StarttlsCommand {
  def handleStarttls: (MailAccumulator, ResponseMessage) =
    response(MailAccumulator.withHello, s"$TLS_NOT_SUPPORTED TLS not available due to temporary reason")

}
