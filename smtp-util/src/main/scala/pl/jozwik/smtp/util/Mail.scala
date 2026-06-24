package pl.jozwik.smtp.util

import org.apache.pekko.util.ByteString

object MailAddress {
  val empty: MailAddress = MailAddress("", "")
}

final case class MailAddress(user: String, domain: String) {
  def isEmpty: Boolean = user.isEmpty

  override def toString: String = s"$user@$domain"
}

final case class Attachment(fileName: String, content: ByteString)

object EmailWithContent {

  def txtOnlyWithoutSubject(from: Seq[MailAddress], to: Seq[MailAddress], txtBody: String): EmailWithContent =
    EmailWithContent(from, to, None, Option(txtBody), None, Seq.empty)

  def txtOnly(from: Seq[MailAddress], to: Seq[MailAddress], subject: String, txtBody: String): EmailWithContent =
    EmailWithContent(from, to, Option(subject), Option(txtBody), None, Seq.empty)

}

final case class EmailWithContent(
    from: Seq[MailAddress],
    to: Seq[MailAddress],
    subject: Option[String],
    txtBody: Option[String],
    htmlBody: Option[String],
    attachments: Seq[Attachment]
) {

  def bodyAsString: String = txtBody.getOrElse("")
}

final case class Mail(from: MailAddress, to: Seq[MailAddress], emailContent: EmailWithContent)

final case class SocketAddress(host: String, port: Int)

sealed trait ConsumedResult

case object SuccessfulConsumed extends ConsumedResult

final case class FailedConsumed(error: String) extends ConsumedResult
