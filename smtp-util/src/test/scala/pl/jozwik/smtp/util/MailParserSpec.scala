package pl.jozwik.smtp
package util

import scala.io.{ Codec, Source }

class MailParserSpec extends AbstractSpec {

  private def fileToString(path: String)(implicit codec: Codec) = {
    val input = getClass.getResourceAsStream(s"/$path")
    val str   = Source.fromInputStream(input).getLines().mkString("\n")
    input.close()
    str
  }

  private def readMail(path: String)(implicit codec: Codec) = {
    val mail         = fileToString(path)
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
      val mail         = MailParser.createTextMessage(Mail(mailAddress, Seq(mailAddress), emailContent))
      logger.debug(s"$mail")
    }

  }

}
