package pl.jozwik.smtp.client

import pl.jozwik.smtp.AbstractSmtpSpec
import pl.jozwik.smtp.util.{ConsumedResult, EmailWithContent, Mail, TestUtils}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.DurationInt

class SmtpServerShutdownSpec extends AbstractSmtpSpec {

  /** Never acknowledges the mail, so the client is still in the middle of the conversation when the server goes down. */
  override protected def consumer(mail: Mail): Future[ConsumedResult] = Promise[ConsumedResult]().future

  "SmtpServerShutdown" should {
    "Fail the mail in progress instead of hanging" in {
      Future {
        TestUtils.sleep(120.millis)
        server.close()
      }
      clientWithActor.sendMail(Mail(mailAddress, Seq(mailAddress), EmailWithContent.txtOnlyWithoutSubject(Seq.empty, Seq.empty, ""))).map {
        case FailedResult(error) =>
          logger.debug(s"$error")
          error should not be empty
        case SuccessResult =>
          fail()
      }
    }
  }

}
