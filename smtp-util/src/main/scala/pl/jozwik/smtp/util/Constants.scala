/*
 * Copyright (c) 2017 Andrzej Jozwik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pl.jozwik.smtp.util

object Constants {
  val OPEN_BRACKET = '<'
  val CLOSE_BRACKET = '>'
  val OPEN_BRACKET_STRING = '<'.toString
  val CLOSE_BRACKET_STRING = '>'.toString
  val SPACE = ' '
  val delimiter = "\n"
  val crLf = s"\r$delimiter"
  val Subject = "Subject"
  val START_MAIL_INPUT = 354
  val CLOSING_TERMINATION_CHANNEL = 221
  val SERVICE_READY = 220
  val REQUEST_COMPLETE = 250
  val CANNOT_VRFY = 252
  val SERVICE_NOT_AVAILABLE = 421
  val REQUEST_ACTION_ABORTED = 451
  val INSUFFICIENT_SYSTEM_STORAGE = 452
  val COMMAND_NOT_IMPLEMENTED = 500
  val SYNTAX_ERROR = 501
  val BAD_SEQUENCE_OF_COMMANDS = 503
  val USER_UNKNOWN = 550
  val SIZE_EXCEEDS_MAXIMUM = 552
  val REQUEST_ACTION_NOT_ALLOWED = 553
  val TRANSACTION_FAILED = 554
  val PARAMETER_UNRECOGNIZED = 555
  val NEED_HELLO = true
  val HELO = "HELO"
  val EHLO = "EHLO"
  val DATA = "DATA"
  val MAIL = "MAIL"
  val FROM = "FROM"
  val QUIT = "QUIT"
  val RCPT = "RCPT"
  val TO = "TO"
  val RSET = "RSET"
  val NOOP = "NOOP"
  val VRFY = "VRFY"
  val NOOP_OK = s"$REQUEST_COMPLETE 2.0.0 OK"
  val RESET_OK = s"$REQUEST_COMPLETE 2.0.0 Reset state"
  val SMTP_OK = s"$REQUEST_COMPLETE OK"
  val MAIL_FROM = s"$MAIL $FROM"
  val RCPT_TO = s"$RCPT $TO"
  val OK_8_BIT = s"$REQUEST_COMPLETE-8BITMIME"
  val OK_SIZE = s"$REQUEST_COMPLETE-SIZE"
  val OK_PIPELINE = s"$SMTP_OK PIPELINE"
  val END_DATA = "."

  val FOUR = HELO.length

  val maximumFrameLength = 1024

}