package pl.jozwik.smtp.util

object Response {

  import SmtpCodes.*

  val BAD_SENDER_ADDRESS_SYNTAX: String = s"$SYNTAX_ERROR 5.1.7 Bad sender address syntax"

  def domainNameRequired(addressWithoutBrackets: String): String =
    s"$REQUEST_ACTION_NOT_ALLOWED 5.5.4 $addressWithoutBrackets ... Domain name required for sender address $addressWithoutBrackets"

  def hostNameRequired(addressWithoutBrackets: String): String =
    s"$REQUEST_ACTION_NOT_ALLOWED 5.1.3 $addressWithoutBrackets ... Hostname required"

  def unbalanced(trimmed: String, c: Char): String = s"$REQUEST_ACTION_NOT_ALLOWED $trimmed 5.0.0 Unbalanced '$c'"

  def parameterUnrecognized(parameter: String): String =
    s"$PARAMETER_UNRECOGNIZED 5.5.4 $parameter parameter unrecognized"

  def senderOk(address: MailAddress): String =
    s"$REQUEST_COMPLETE 2.1.0 $address... Sender ok"

  def recipientOk(address: MailAddress): String =
    s"$REQUEST_COMPLETE 2.1.5 $address... Recipient ok"

  def closingChannel(hostName: String): String = s"$CLOSING_TERMINATION_CHANNEL 2.0.0 $hostName closing connection"

  def sizeExceedsMaximum(size: Long): String = s"$SIZE_EXCEEDS_MAXIMUM 5.2.3 Message size exceeds maximum value: $size"

}
