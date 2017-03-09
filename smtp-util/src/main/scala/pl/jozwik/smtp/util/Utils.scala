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
package util

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.regex.Pattern

object Utils {

  import Constants._
  import Response._

  val WHITE_SPACES_PATTERN = Pattern.compile("""\s+""")

  def now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)

  def withEndOfLine(line: String): String = s"$line$endOfLine"

  def splitLineByColon(message: String): (String, String) = {
    val (head, argument) = message.indexOf(":") match {
      case -1 =>
        (message, "")
      case x: Int =>
        (message.substring(0, x), message.substring(x + 1))
    }
    (head.trim, argument.trim)
  }

  def splitOnWhiteSpaces(txt: String, limit: Int = 0): Seq[String] =
    WHITE_SPACES_PATTERN.split(txt, limit)

  def extractAddressAndParameters(txt: String): Either[String, (String, Map[String, String])] =
    extractMailAddress(txt.trim) match {
      case Right((addressWithoutBrackets, parameters)) =>
        validateBrackets(addressWithoutBrackets, address =>
          parametersToMap(parameters) match {
            case Right(map) =>
              Right((address, map))
            case Left(error) =>
              Left(error)
          })

      case Left(error) =>
        Left(error)
    }

  def parametersToMap(seq: Seq[String]): Either[String, Map[String, String]] = {
    def parametersToMap(seq: Seq[String], map: Map[String, String]): Either[String, Map[String, String]] = seq match {
      case Seq() =>
        Right(map)
      case h +: t =>
        h.split('=') match {
          case Array(k, v) =>
            parametersToMap(t, map + (k.trim.toUpperCase -> v.trim))
          case _ =>
            Left(Response.parameterUnrecognized(h))
        }
    }
    parametersToMap(seq, Map.empty)

  }

  private def extractMailAddress(maybeInBrackets: String): Either[String, (String, Seq[String])] =
    (maybeInBrackets.startsWith(OPEN_BRACKET_STRING), maybeInBrackets.lastIndexOf(CLOSE_BRACKET)) match {
      case (true, end) =>
        extractMailAddressCloseBracket(maybeInBrackets, end)
      case (false, end) if end != -1 =>
        Left(unbalanced(maybeInBrackets, CLOSE_BRACKET))
      case _ =>
        Right(extractMailAddressWithoutBrackets(maybeInBrackets))
    }

  private def extractMailAddressWithoutBrackets(notInBrackets: String): (String, Seq[String]) =
    splitOnWhiteSpaces(notInBrackets, 2) match {
      case Seq(address, parameters) =>
        (address, splitOrEmpty(parameters))
      case Seq(address) =>
        (address, Seq.empty)
    }

  private def extractMailAddressCloseBracket(inBrackets: String, end: Int): Either[String, (String, Seq[String])] = {
    if (end == -1) {
      Left(unbalanced(inBrackets, OPEN_BRACKET))
    } else {
      val address = removeDoubleBrackets(inBrackets.substring(1, end))
      val parameters = inBrackets.substring(end + 1)
      if (parameters.headOption.getOrElse(SPACE) == SPACE) {
        Right((address, splitOrEmpty(parameters.trim)))
      } else {
        Left(BAD_SENDER_ADDRESS_SYNTAX)
      }
    }
  }

  private def removeDoubleBrackets(maybeInBrackets: String): String = {
    val trimmed = maybeInBrackets.trim
    if (trimmed.startsWith(OPEN_BRACKET_STRING) && trimmed.endsWith(CLOSE_BRACKET_STRING)) {
      trimmed.substring(1, trimmed.length - 1)
    } else {
      trimmed
    }
  }

  private def splitOrEmpty(txt: String) = if (txt.isEmpty) Seq.empty else splitOnWhiteSpaces(txt)

  def toMailAddress(txt: String): Either[String, MailAddress] =
    cutBrackets(txt.trim) match {
      case Left(error) =>
        Left(error)
      case Right(withoutBrackets) =>
        validateBrackets(withoutBrackets, notEmptyStringToMailAddress)
    }

  private def validateBrackets[T](
    withoutBrackets: String,
    f: (String => Either[String, T])
  ): Either[String, T] =
    (withoutBrackets.contains(CLOSE_BRACKET), withoutBrackets.contains(OPEN_BRACKET)) match {
      case (true, _) =>
        Left(unbalanced(withoutBrackets, CLOSE_BRACKET))
      case (_, true) =>
        Left(unbalanced(withoutBrackets, OPEN_BRACKET))
      case _ =>
        f(withoutBrackets)
    }

  private def notEmptyStringToMailAddress(addressWithoutBrackets: String): Either[String, MailAddress] =
    addressWithoutBrackets.indexOf('@') match {
      case -1 =>
        Left(domainNameRequired(addressWithoutBrackets))
      case index: Int if index != 0 && index != addressWithoutBrackets.length - 1 =>
        val user = addressWithoutBrackets.substring(0, index)
        val domain = addressWithoutBrackets.substring(index + 1, addressWithoutBrackets.length)
        Right(MailAddress(user, domain.toLowerCase))
      case _ =>
        Left(hostNameRequired(addressWithoutBrackets))
    }

  private def cutBrackets(addressWithBrackets: String): Either[String, String] =
    (addressWithBrackets.startsWith(OPEN_BRACKET_STRING), addressWithBrackets.endsWith(CLOSE_BRACKET_STRING)) match {
      case (true, true) =>
        Right(addressWithBrackets.substring(1, addressWithBrackets.length - 1).trim)
      case (true, false) =>
        Left(unbalanced(addressWithBrackets, CLOSE_BRACKET))
      case (false, true) =>
        Left(unbalanced(addressWithBrackets, OPEN_BRACKET))
      case (false, false) =>
        Right(addressWithBrackets)
    }

  def extractMessage(lines: IndexedSeq[String]): EmailContent =
    MailParser.parse(lines.mkString)

}

object RuntimeConstants {
  val portKey = "smtp.port"
  val sizeKey = "smtp.size"
}

object Response {

  import Constants._

  val BAD_SENDER_ADDRESS_SYNTAX = s"$SYNTAX_ERROR 5.1.7 Bad sender address syntax"

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