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

object MailAddress {
  val empty = MailAddress("", "")
}

final case class MailAddress(user: String, domain: String) {
  def isEmpty: Boolean = user.isEmpty

  override def toString: String = s"$user@$domain"
}

final case class Attachment(fileName: String, content: Array[Byte])

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
