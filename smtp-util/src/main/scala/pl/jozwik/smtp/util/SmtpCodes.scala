package pl.jozwik.smtp.util

object SmtpCodes {
  val SERVICE_READY: Int               = 220
  val CLOSING_TERMINATION_CHANNEL: Int = 221
  val REQUEST_COMPLETE: Int            = 250
  val CANNOT_VRFY: Int                 = 252
  val START_MAIL_INPUT: Int            = 354
  val SERVICE_NOT_AVAILABLE: Int       = 421
  val REQUEST_ACTION_ABORTED: Int      = 451
  val INSUFFICIENT_SYSTEM_STORAGE: Int = 452
  val TLS_NOT_SUPPORTED: Int           = 454
  val COMMAND_NOT_IMPLEMENTED: Int     = 500
  val SYNTAX_ERROR: Int                = 501
  val BAD_SEQUENCE_OF_COMMANDS: Int    = 503
  val USER_UNKNOWN: Int                = 550
  val SIZE_EXCEEDS_MAXIMUM: Int        = 552
  val REQUEST_ACTION_NOT_ALLOWED: Int  = 553
  val TRANSACTION_FAILED: Int          = 554
  val PARAMETER_UNRECOGNIZED: Int      = 555
}
