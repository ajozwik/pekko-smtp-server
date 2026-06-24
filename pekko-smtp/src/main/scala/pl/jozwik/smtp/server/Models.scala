package pl.jozwik.smtp
package server

import java.time.ZonedDateTime

import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.Utils.*
import pl.jozwik.smtp.util.{ MailAddress, SizeParameterHandler }

import scala.concurrent.duration.*

final case class Configuration(port: Int, size: Long = SizeParameterHandler.DefaultMailSize, readTimeout: FiniteDuration = 1.hour)

private[server] case object TickTimeout

final case class Content(content: IndexedSeq[String] = IndexedSeq.empty[String], size: Int = 0)

sealed trait ResponseMessage

final case class MultiLineResponse(message: Seq[String]) extends ResponseMessage

final case class TextResponse(message: String) extends ResponseMessage

case object NoResponse extends ResponseMessage

case object NoDataResponse extends ResponseMessage

final case class QuitResponse(message: String) extends ResponseMessage

object MailAccumulator {
  val empty: MailAccumulator     = MailAccumulator(NeedHello)
  val withHello: MailAccumulator = MailAccumulator(!NeedHello)
}

final case class MailAccumulator(
    needHello: Boolean = false,
    from: MailAddress = MailAddress.empty,
    to: Seq[MailAddress] = Seq.empty[MailAddress],
    content: Content = Content(),
    readData: Boolean = false,
    notCompletedLine: Option[String] = None,
    lastMessageTimestamp: ZonedDateTime = now,
    tls: Boolean = false
) {

  def addLine(line: String): MailAccumulator = {
    this.copy(content = Content(content.content :+ line, content.size + line.length))
  }

}
