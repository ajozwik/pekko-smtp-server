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

import com.typesafe.scalalogging.StrictLogging
import org.apache.james.mime4j.dom._
import org.apache.james.mime4j.message._
import org.apache.james.mime4j.message
import java.nio.charset.StandardCharsets

import scala.collection.JavaConverters._

object AccEmailContent {
  val empty = AccEmailContent(IndexedSeq.empty, IndexedSeq.empty, Seq.empty)
}

case class AccEmailContent(txtBody: IndexedSeq[String], htmlBody: IndexedSeq[String], attachments: Seq[Attachment])

@SuppressWarnings(Array("AsInstanceOf"))
object MailParser {
  private val messageBuilder = new DefaultMessageBuilder
  private val writer = new DefaultMessageWriter

  def parse(mailAsTxt: String): EmailContent = {
    val parser = new MailParser
    val mimeMsg = messageBuilder.parseMessage(new ByteArrayInputStream(mailAsTxt.getBytes))
    parser.parseMessage(mimeMsg)
  }

  private[smtp] def createTextMessage(mail: Mail): String = {
    val emailContent = mail.emailContent
    val body = new BasicBodyFactory().textBody(emailContent.txtBody.getOrElse(""), StandardCharsets.UTF_8)
    val builder = message.MessageBuilder.create.setSubject(emailContent.subject.orNull).setBody(body)
    val out = new ByteArrayOutputStream()
    writer.writeMessage(builder.build, out)
    out.toString
  }

  private def getTxtPart(part: Entity): String = {
    val textBody = part.getBody.asInstanceOf[SingleBody]
    val bytes = toByteArray(textBody)
    new String(bytes)
  }

  private def toOption(seq: IndexedSeq[String]): Option[String] = {
    if (seq.isEmpty) {
      None
    } else {
      Option(seq.mkString)
    }
  }

  private def addMultipart(part: BodyPart, accWithPart: AccEmailContent): AccEmailContent =
    if (part.isMultipart) {
      parseBodyParts(part.getBody.asInstanceOf[Multipart].getBodyParts.asScala, accWithPart)
    } else {
      accWithPart
    }

  private def addPart(acc: AccEmailContent, part: BodyPart): AccEmailContent =
    part match {
      case _ if part.getMimeType.toLowerCase == "text/plain" =>
        val txt = getTxtPart(part)
        acc.copy(txtBody = acc.txtBody :+ txt)
      case _ if part.getMimeType.toLowerCase == "text/html" =>
        val html = getTxtPart(part)
        acc.copy(htmlBody = acc.htmlBody :+ html)
      case _ if Option(part.getDispositionType).exists(_ != "") =>
        val bb = part.getBody.asInstanceOf[SingleBody]
        val bytes = toByteArray(bb)
        acc.copy(attachments = Attachment(part.getFilename, bytes) +: acc.attachments)
      case _ =>
        acc
    }

  private def parseBodyParts(entities: Seq[Entity], acc: AccEmailContent): AccEmailContent = entities match {
    case Seq() =>
      acc
    case head +: tail =>
      val part = head.asInstanceOf[BodyPart]
      val accWithPart = addPart(acc, part)
      val accWithMultipart = addMultipart(part, accWithPart)
      parseBodyParts(tail, accWithMultipart)
  }

  private def toByteArray(singleBody: SingleBody) = {
    val fos = new ByteArrayOutputStream()
    singleBody.writeTo(fos)
    fos.toByteArray
  }
}

@SuppressWarnings(Array("AsInstanceOf"))
class MailParser extends StrictLogging {

  import MailParser._

  def parseMessage(mimeMsg: Message): EmailContent = {
    val subject = Option(mimeMsg.getSubject)
    logger.debug(s"To: ${mimeMsg.getTo}")
    logger.debug(s"From: ${mimeMsg.getFrom}")
    logger.debug(s"Subject: $subject")
    if (mimeMsg.isMultipart) {
      val multipart = mimeMsg.getBody.asInstanceOf[Multipart]
      val acc = parseBodyParts(multipart.getBodyParts.asScala, AccEmailContent.empty)
      val htmlBody = toOption(acc.htmlBody)
      val txtBody = toOption(acc.txtBody)
      EmailContent(subject, txtBody, htmlBody, acc.attachments)
    } else {
      val text = getTxtPart(mimeMsg)
      EmailContent(subject, Option(text), None, Seq.empty)
    }
  }

}