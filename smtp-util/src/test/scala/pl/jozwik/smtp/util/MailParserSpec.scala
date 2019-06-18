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

import scala.io.{ Codec, Source }

class MailParserSpec extends AbstractSpec {

  private def fileToString(path: String)(implicit codec: Codec) = {
    val input = getClass.getResourceAsStream(s"/$path")
    val str = Source.fromInputStream(input).getLines().mkString("\n")
    input.close()
    str
  }

  private def readMail(path: String)(implicit codec: Codec) = {
    val mail = fileToString(path)
    val emailContent = MailParser.parse(mail)
    logger.debug(s"$emailContent")
    emailContent
  }

  "Parser " should {
    "parse mail " in {
      val emailContent = readMail("codacy.eml")
      emailContent.attachments shouldBe empty
    }

    "mail with attachment " in {
      val emailContent = readMail("attachment.eml")
      emailContent.attachments should not be empty
    }

    "for wrong encoding test only" in {
      val emailContent = readMail("smile.eml")(Codec.ISO8859)
      emailContent.subject should not be None
    }

    "Create email " in {
      val emailContent = EmailWithContent.txtOnly(Seq.empty, Seq.empty, "--Subject--", "--Text--")
      val mail = MailParser.createTextMessage(Mail(mailAddress, Seq(mailAddress), emailContent))
      logger.debug(s"$mail")
    }

  }
}