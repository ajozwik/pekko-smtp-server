package pl.jozwik.smtp
package util

import java.net.InetAddress
import java.time.{ ZoneOffset, ZonedDateTime }
import java.util.regex.Pattern
import com.typesafe.scalalogging.StrictLogging

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.util.Try

object Utils {

  import Constants.*
  import Response.*

  private val WHITE_SPACES_PATTERN = Pattern.compile("""\s+""")

  def now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)

  def withEndOfLine(line: String): String = s"$line$CrLf"

  def splitLineByColon(message: String): (String, String) = {
    val (head, argument) = message.indexOf(":") match {
      case -1 =>
        (message, "")
      case x: Int =>
        (message.substring(0, x), message.substring(x + 1))
    }
    (head.trim, argument.trim)
  }

  def splitOnWhiteSpaces(txt: String, limit: Int = 0): IndexedSeq[String] =
    ArraySeq.unsafeWrapArray(WHITE_SPACES_PATTERN.split(txt, limit))

  def extractAddressAndParameters(txt: String): Either[String, (String, Map[String, String])] =
    extractMailAddress(txt.trim) match {
      case Right((addressWithoutBrackets, parameters)) =>
        validateBrackets(
          addressWithoutBrackets,
          address =>
            parametersToMap(parameters) match {
              case Right(map) =>
                Right((address, map))
              case Left(error) =>
                Left(error)
            }
        )

      case Left(error) =>
        Left(error)
    }

  private def parametersToMap(seq: Seq[String]): Either[String, Map[String, String]] = {
    @tailrec
    def parametersToMap(seq: Seq[String], map: Map[String, String]): Either[String, Map[String, String]] = seq match {
      case h +: t =>
        h.split('=') match {
          case Array(k, v) =>
            parametersToMap(t, map + (k.trim.toUpperCase(Constants.LocaleRoot) -> v.trim))
          case _ =>
            Left(Response.parameterUnrecognized(h))
        }
      case _ =>
        Right(map)
    }

    parametersToMap(seq, Map.empty)

  }

  private def extractMailAddress(maybeInBrackets: String): Either[String, (String, Seq[String])] =
    (maybeInBrackets.startsWith(OpenBracketString), maybeInBrackets.lastIndexOf(CloseBracket)) match {
      case (true, end) =>
        extractMailAddressCloseBracket(maybeInBrackets, end)
      case (false, end) if end != -1 =>
        Left(unbalanced(maybeInBrackets, CloseBracket))
      case _ =>
        Right(extractMailAddressWithoutBrackets(maybeInBrackets))
    }

  private def extractMailAddressWithoutBrackets(notInBrackets: String): (String, Seq[String]) =
    splitOnWhiteSpaces(notInBrackets, 2) match {
      case Seq(address, parameters) =>
        (address, splitOrEmpty(parameters))
      case seq =>
        (unsafeHead(seq), Seq.empty)
    }

  private def extractMailAddressCloseBracket(inBrackets: String, end: Int): Either[String, (String, Seq[String])] = {
    if (end == -1) {
      Left(unbalanced(inBrackets, OpenBracket))
    } else {
      val address    = removeDoubleBrackets(inBrackets.substring(1, end))
      val parameters = inBrackets.substring(end + 1)
      if (parameters.headOption.getOrElse(Space) == Space) {
        Right((address, splitOrEmpty(parameters.trim)))
      } else {
        Left(BAD_SENDER_ADDRESS_SYNTAX)
      }
    }
  }

  private def removeDoubleBrackets(maybeInBrackets: String): String = {
    val trimmed = maybeInBrackets.trim
    if (trimmed.startsWith(OpenBracketString) && trimmed.endsWith(CloseBracketString)) {
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

  private def validateBrackets[T](withoutBrackets: String, f: String => Either[String, T]): Either[String, T] =
    (withoutBrackets.contains(CloseBracket), withoutBrackets.contains(OpenBracket)) match {
      case (true, _) =>
        Left(unbalanced(withoutBrackets, CloseBracket))
      case (_, true) =>
        Left(unbalanced(withoutBrackets, OpenBracket))
      case _ =>
        f(withoutBrackets)
    }

  private def notEmptyStringToMailAddress(addressWithoutBrackets: String): Either[String, MailAddress] =
    addressWithoutBrackets.indexOf('@') match {
      case -1 =>
        Left(domainNameRequired(addressWithoutBrackets))
      case index: Int if index != 0 && index != addressWithoutBrackets.length - 1 =>
        val user   = addressWithoutBrackets.substring(0, index)
        val domain = addressWithoutBrackets.substring(index + 1, addressWithoutBrackets.length)
        Right(MailAddress(user, domain.toLowerCase(Constants.LocaleRoot)))
      case _ =>
        Left(hostNameRequired(addressWithoutBrackets))
    }

  private def cutBrackets(addressWithBrackets: String): Either[String, String] =
    (addressWithBrackets.startsWith(OpenBracketString), addressWithBrackets.endsWith(CloseBracketString)) match {
      case (true, true) =>
        Right(addressWithBrackets.substring(1, addressWithBrackets.length - 1).trim)
      case (true, false) =>
        Left(unbalanced(addressWithBrackets, CloseBracket))
      case (false, true) =>
        Left(unbalanced(addressWithBrackets, OpenBracket))
      case (false, false) =>
        Right(addressWithBrackets)
    }

  def extractMessage(lines: IndexedSeq[String]): EmailWithContent =
    MailParser.parse(lines.mkString)

  def unsafeHead[S](seq: Iterable[S]): S =
    seq.headOption.getOrElse(throw new NoSuchElementException())

}

object RuntimeConstants {
  val portKey       = "smtp.port"
  val sizeKey       = "smtp.size"
  val consumerClass = "consumer.class"
}

object IOUtils extends StrictLogging {

  import sys.env

  val defaultHostName = "127.0.0.1"

  def hostnameFromEnvVariable(name: String): String = env.getOrElse(name, defaultHostName)

  def localHostName: String = Try(InetAddress.getLocalHost.getHostName).getOrElse(hostnameFromEnvVariable("HOSTNAME"))
}
