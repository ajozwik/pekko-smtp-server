package pl.jozwik.smtp

import pl.jozwik.smtp.client.{ FailedResult, SenderClient, SuccessResult }
import pl.jozwik.smtp.util.{ EmailWithContent, Mail, Utils }

class SmtpStreamIntegrationSpec extends AbstractSmtpIntegrationSpec {
  protected val client: SenderClient = clientStream
}

class SmtpActorIntegrationSpec extends AbstractSmtpIntegrationSpec {
  protected val client: SenderClient = clientWithActor
}

abstract class AbstractSmtpIntegrationSpec extends AbstractSmtpSpec {

  protected val client: SenderClient

  "Smtp integration test" should {

    "finished without error" in {

      val mail   = Mail(mailAddress, Seq(mailAddress), EmailWithContent.txtOnly(Seq.empty, Seq.empty, "My Subject", "Content"))
      val future = client.sendMail(mail)
      future.map { _ shouldBe SuccessResult }

    }

    "Too much data" in {
      val line = Utils.withEndOfLine("Content")
      val size = maxSize / line.length + 1
      logger.debug(s"$size $maxSize ${line.length}")
      val largeContent = Seq.fill(size)(line).mkString
      logger.debug(s"$size $maxSize ${line.length} ${largeContent.length}")
      val mail   = Mail(mailAddress, Seq(mailAddress), EmailWithContent.txtOnly(Seq.empty, Seq.empty, "My Subject", largeContent))
      val future = client.sendMail(mail)
      future.map { result =>
        result shouldBe a[FailedResult]
      }

    }

  }

}
