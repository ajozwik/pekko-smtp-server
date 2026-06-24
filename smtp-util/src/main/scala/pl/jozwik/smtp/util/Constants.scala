package pl.jozwik.smtp.util

import SmtpCodes.*

import java.util.Locale

object Constants {
  val CloseBracket               = '>'
  val CloseBracketString: String = '>'.toString
  val OpenBracket                = '<'
  val OpenBracketString: String  = '<'.toString
  val Space                      = ' '
  val Delimiter                  = "\n"
  val CrLf: String               = s"\r$Delimiter"
  val Subject                    = "Subject"
  val LocaleRoot: Locale         = Locale.ROOT
  val NeedHello                  = true
  val MaximumFrameLength: Int    = 1024 * 16

  val DATA              = "DATA"
  val EHLO              = "EHLO"
  val FROM              = "FROM"
  val HELO              = "HELO"
  val MAIL              = "MAIL"
  val NOOP              = "NOOP"
  val QUIT              = "QUIT"
  val RCPT              = "RCPT"
  val RSET              = "RSET"
  val STARTTLS          = "STARTTLS"
  val TO                = "TO"
  val VRFY              = "VRFY"
  val MAIL_FROM: String = s"$MAIL $FROM"
  val RCPT_TO: String   = s"$RCPT $TO"
  val RESET_OK: String  = s"$REQUEST_COMPLETE 2.0.0 Reset state"
  val END_DATA          = "."
  val Four: Int         = HELO.length

}
