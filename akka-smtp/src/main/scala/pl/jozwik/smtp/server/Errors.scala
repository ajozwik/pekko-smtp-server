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
package pl.jozwik.smtp
package server

import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.MailAddress

object Errors {
  def insufficientSystemStorage(size: Long): String = s"$INSUFFICIENT_SYSTEM_STORAGE max size of message is $size"
  def domainNotResolved(address: MailAddress): String = s"""$REQUEST_ACTION_ABORTED 4.1.8 Domain of sender address $address does not resolve"""
  def syntaxError(parameter: String): String = s"""$SYNTAX_ERROR 5.5.2 Syntax error in parameters scanning `$parameter`"""
  def commandNotRecognized(line: String): String = s"$COMMAND_NOT_IMPLEMENTED 5.5.1 Command not recognized `$line`"
  def serviceNotAvailable(hostName: String, timeoutSeconds: Long): String = s"$SERVICE_NOT_AVAILABLE 4.4.2 $hostName Read timeout $timeoutSeconds seconds."
  def userUnknown(mailAddress: MailAddress): String = s"""$USER_UNKNOWN 5.1.1 $mailAddress... User unknown"""
  val SENDER_ALREADY_SPECIFIED = s"$BAD_SEQUENCE_OF_COMMANDS 5.5.0 Sender already specified"
  val MAIL_MISSING = s"$BAD_SEQUENCE_OF_COMMANDS 5.0.0 Need $MAIL command before $RCPT"
  val RCPT_MISSING = s"$BAD_SEQUENCE_OF_COMMANDS 5.0.0 Need $RCPT (recipient)"
  val START_INPUT = s"""$START_MAIL_INPUT Enter mail, end with "." on a line by itself"""
  val CANNOT_VERIFY = s"$CANNOT_VRFY Cannot VRFY user; try RCPT to attempt delivery (or try finger)"
  val HELLO_FIRST = s"$BAD_SEQUENCE_OF_COMMANDS 5.5.1 Error: send $HELO/$EHLO first"
}