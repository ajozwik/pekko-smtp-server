package pl.jozwik.smtp
package server

import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.SmtpCodes.*
import pl.jozwik.smtp.util.MailAddress

object Errors {
  def insufficientSystemStorage(size: Long): String   = s"$INSUFFICIENT_SYSTEM_STORAGE max size of message is $size"
  def domainNotResolved(address: MailAddress): String = s"""$REQUEST_ACTION_ABORTED 4.1.8 Domain of sender address $address does not resolve"""
  def syntaxError(parameter: String): String          = s"""$SYNTAX_ERROR 5.5.2 Syntax error in parameters scanning `$parameter`"""
  def commandNotRecognized(line: String): String      = s"$COMMAND_NOT_IMPLEMENTED 5.5.1 Command not recognized `$line`"
  def serviceNotAvailable(hostName: String, timeoutSeconds: Long): String = s"$SERVICE_NOT_AVAILABLE 4.4.2 $hostName Read timeout $timeoutSeconds seconds."
  def userUnknown(mailAddress: MailAddress): String                       = s"""$USER_UNKNOWN 5.1.1 $mailAddress... User unknown"""
  val SENDER_ALREADY_SPECIFIED: String                                    = s"$BAD_SEQUENCE_OF_COMMANDS 5.5.0 Sender already specified"
  val MAIL_MISSING: String                                                = s"$BAD_SEQUENCE_OF_COMMANDS 5.0.0 Need $MAIL command before $RCPT"
  val RCPT_MISSING: String                                                = s"$BAD_SEQUENCE_OF_COMMANDS 5.0.0 Need $RCPT (recipient)"
  val START_INPUT: String                                                 = s"""$START_MAIL_INPUT Enter mail, end with "$END_DATA" on a line by itself"""
  val CANNOT_VERIFY: String                                               = s"$CANNOT_VRFY Cannot VRFY user; try RCPT to attempt delivery (or try finger)"
  val HELLO_FIRST: String                                                 = s"$BAD_SEQUENCE_OF_COMMANDS 5.5.1 Error: send $HELO/$EHLO first"
}
