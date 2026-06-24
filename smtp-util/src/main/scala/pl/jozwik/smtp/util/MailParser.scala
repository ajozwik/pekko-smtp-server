package pl.jozwik.smtp
package util

import java.io.*
import java.nio.charset.StandardCharsets
import com.typesafe.scalalogging.StrictLogging
import org.apache.james.mime4j.dom.Message.Builder
import org.apache.james.mime4j.dom.*
import org.apache.james.mime4j.dom.address.MailboxList
import org.apache.james.mime4j.message.*
import org.apache.pekko.util.ByteString

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

object TmpEmailContent {
  val empty: TmpEmailContent = TmpEmailContent(IndexedSeq.empty, IndexedSeq.empty, Seq.empty)
}

private[util] final case class TmpEmailContent(txtBody: IndexedSeq[String], htmlBody: IndexedSeq[String], attachments: Seq[Attachment])

object MailParser extends StrictLogging {
  private[util] val messageBuilder = new DefaultMessageBuilder
  private val writer               = new DefaultMessageWriter

  def parse(mailAsTxt: String): EmailWithContent = {
    val mimeMsg = toMessage(mailAsTxt)
    parseMessage(mimeMsg)
  }

  private[util] def toMessage(mailAsTxt: String) =
    messageBuilder.parseMessage(new ByteArrayInputStream(mailAsTxt.getBytes(StandardCharsets.UTF_8)))

  private[util] def parseMessage(mimeMsg: Message): EmailWithContent = {
    val subject = Option(mimeMsg.getSubject)
    val from    = toList(Option(mimeMsg.getFrom))
    val to      = toList(Option(mimeMsg.getTo).map(_.flatten()))
    logger.debug(s"To: ${mimeMsg.getTo}")
    logger.debug(s"From: ${mimeMsg.getFrom}")
    logger.debug(s"Subject: $subject")
    mimeMsg.getBody match {
      case multipart: Multipart =>
        val acc      = parseBodyParts(multipart.getBodyParts.asScala.toSeq, TmpEmailContent.empty)
        val htmlBody = toOption(acc.htmlBody)
        val txtBody  = toOption(acc.txtBody)
        EmailWithContent(from, to, subject, txtBody, htmlBody, acc.attachments)
      case body: SingleBody =>
        val text = getTxtPart(body)
        EmailWithContent(from, to, subject, Option(text), None, Seq.empty)
      case x: Any =>
        throw new UnsupportedOperationException(s"$x")
    }
  }

  private def toList(mailboxList: Option[MailboxList]): Seq[MailAddress] =
    mailboxList
      .map { list =>
        (0 until list.size()).map { i =>
          val el     = list.get(i)
          val name   = el.getLocalPart
          val domain = el.getDomain
          logger.debug(s"${el.getAddress} ${el.getName}")
          MailAddress(name, domain)
        }
      }
      .getOrElse(Seq.empty)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private[smtp] def createTextMessage(mail: Mail): String = {
    val emailContent = mail.emailContent
    val body         = new BasicBodyFactory().textBody(emailContent.bodyAsString, StandardCharsets.UTF_8)
    val builder      = Builder.of().setSubject(emailContent.subject.orNull).setBody(body)
    val out          = new ByteArrayOutputStream()
    writer.writeMessage(builder.build, out)
    out.toString
  }

  private def getTxtPart(singleBody: SingleBody): String = {
    val bytes = toByteArray(singleBody)
    new String(bytes, StandardCharsets.UTF_8)
  }

  private def toOption(seq: IndexedSeq[String]): Option[String] = {
    if (seq.isEmpty) {
      None
    } else {
      Option(seq.mkString)
    }
  }

  private def addMultipart(m: Multipart, accWithPart: TmpEmailContent): TmpEmailContent =
    parseBodyParts(m.getBodyParts.asScala.toSeq, accWithPart)

  private def addSingleBody(acc: TmpEmailContent, mime: String, b: SingleBody, part: Entity): TmpEmailContent =
    mime match {
      case "text/plain" =>
        val txt = getTxtPart(b)
        acc.copy(txtBody = acc.txtBody :+ txt)
      case "text/html" =>
        val html = getTxtPart(b)
        acc.copy(htmlBody = acc.htmlBody :+ html)
      case _ if Option(part.getDispositionType).exists(_ != "") =>
        val bytes = toByteArray(b)
        acc.copy(attachments = Attachment(part.getFilename, ByteString(bytes)) +: acc.attachments)
      case _ =>
        acc
    }

  @tailrec
  private def parseBodyParts(entities: Seq[Entity], acc: TmpEmailContent): TmpEmailContent = entities match {
    case part +: tail =>
      val accWithPart =
        part.getBody match {
          case singleBody: SingleBody =>
            addSingleBody(acc, part.getMimeType.toLowerCase(Constants.LocaleRoot), singleBody, part)
          case m: Multipart =>
            addMultipart(m, acc)
          case b =>
            throw new UnsupportedOperationException(s"${b.getClass}")

        }
      parseBodyParts(tail, accWithPart)
    case _ =>
      acc
  }

  private def toByteArray(singleBody: SingleBody) = {
    val fos = new ByteArrayOutputStream()
    singleBody.writeTo(fos)
    fos.toByteArray
  }

}
