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

import java.io._
import java.nio.charset.StandardCharsets

import com.typesafe.scalalogging.StrictLogging
import org.apache.james.mime4j.dom.Message.Builder
import org.apache.james.mime4j.dom._
import org.apache.james.mime4j.dom.address.MailboxList
import org.apache.james.mime4j.message._

import scala.jdk.CollectionConverters._

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
    messageBuilder.parseMessage(new ByteArrayInputStream(mailAsTxt.getBytes))

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
      case x =>
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
    new String(bytes)
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
        acc.copy(attachments = Attachment(part.getFilename, bytes) +: acc.attachments)
      case _ =>
        acc
    }

  private def parseBodyParts(entities: Seq[Entity], acc: TmpEmailContent): TmpEmailContent = entities match {
    case part +: tail =>
      val accWithPart =
        part.getBody match {
          case singleBody: SingleBody =>
            addSingleBody(acc, part.getMimeType.toLowerCase, singleBody, part)
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
