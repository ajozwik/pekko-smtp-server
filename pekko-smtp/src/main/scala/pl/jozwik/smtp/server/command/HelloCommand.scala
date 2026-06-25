package pl.jozwik.smtp
package server
package command

import pl.jozwik.smtp.util.SmtpCodes.REQUEST_COMPLETE

import java.net.InetSocketAddress
import pl.jozwik.smtp.util.SmtpResponses.*

object HelloCommand {

  def handleEhlo(localHostName: String, remote: InetSocketAddress, size: Long): (MailAccumulator, ResponseMessage) = {
    val welcomeLine =
      s"$REQUEST_COMPLETE-$localHostName Hello ${remote.getHostName} " +
        s"[${remote.getAddress.getHostAddress}] pleased to meet you."

    response(MailAccumulator.withHello, welcomeLine, OK_8_BIT, s"$OK_SIZE $size", TLS_OK_RESPONSE, OK_PIPELINE)

  }

  def handleHelo(localHostName: String, remote: InetSocketAddress): (MailAccumulator, ResponseMessage) = {
    val welcomeLine =
      s"$REQUEST_COMPLETE $localHostName Hello ${remote.getHostName} " +
        s"[${remote.getAddress.getHostAddress}] pleased to meet you."
    response(MailAccumulator.withHello, welcomeLine)

  }

}
