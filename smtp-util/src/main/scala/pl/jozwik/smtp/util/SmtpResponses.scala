package pl.jozwik.smtp.util

import pl.jozwik.smtp.util.SmtpCodes.{ REQUEST_COMPLETE, SERVICE_READY, TLS_NOT_SUPPORTED }

object SmtpResponses {
  val NOOP_OK: String                = s"$REQUEST_COMPLETE 2.0.0 OK"
  val OK_8_BIT: String               = s"$REQUEST_COMPLETE-8BITMIME"
  val SMTP_OK: String                = s"$REQUEST_COMPLETE OK"
  val OK_PIPELINE: String            = s"$SMTP_OK PIPELINE"
  val OK_SIZE: String                = s"$REQUEST_COMPLETE-SIZE"
  val TLS_SUPPORTED_RESPONSE: String = s"$SERVICE_READY Ready to start TLS"
  val TLS_OK_RESPONSE: String        = s"$REQUEST_COMPLETE-STARTTLS"
}
