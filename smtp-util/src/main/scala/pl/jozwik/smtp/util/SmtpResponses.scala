package pl.jozwik.smtp.util

import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.util.SmtpCodes.*

object SmtpResponses {
  val HANDSHAKE_RESPONSE: String         = s"$SERVER_CHALLENGE STARTTLS"
  val HANDSHAKE_RESPONSE_BS: ByteString  = ByteString(Utils.withEndOfLine(s"$SERVER_CHALLENGE STARTTLS"))
  val TLS_NOT_SUPPORTED_RESPONSE: String = s"$TLS_NOT_SUPPORTED TLS not supported"
  val NOOP_OK: String                    = s"$REQUEST_COMPLETE 2.0.0 OK"
  val OK_8_BIT: String                   = s"$REQUEST_COMPLETE-8BITMIME"
  val SMTP_OK: String                    = s"$REQUEST_COMPLETE OK"
  val OK_PIPELINE: String                = s"$SMTP_OK PIPELINE"
  val OK_SIZE: String                    = s"$REQUEST_COMPLETE-SIZE"
  val TLS_SUPPORTED_RESPONSE: String     = s"$SERVICE_READY Ready to start TLS"
  val TLS_OK_RESPONSE: String            = s"$REQUEST_COMPLETE-STARTTLS"
}
