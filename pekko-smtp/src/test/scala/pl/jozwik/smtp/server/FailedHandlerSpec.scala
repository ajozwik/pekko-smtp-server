package pl.jozwik.smtp
package server

import pl.jozwik.smtp.client.{FailedResult, SuccessResult}
import pl.jozwik.smtp.util.*

import scala.concurrent.Future

class FailedConsumerHandlerSpec extends FailedHandlerSpec {
  override protected def consumer(mail: Mail): Future[ConsumedResult] = Future.successful(FailedConsumed("Always failed"))
}

abstract class FailedHandlerSpec extends AbstractSmtpSpec {
  private val notAcceptedDomain = "notaccepted"
  private val userUnknown       = "userUnknown"

  override protected def addressHandler: AddressHandler = new AddressHandler {
    def acceptFrom(from: MailAddress): Boolean =
      from.domain != notAcceptedDomain

    def acceptTo(to: MailAddress): Boolean =
      to.user != userUnknown
  }

  private def shouldFailed[T](mail: Mail) = {
    val f = clientStream.sendMail(mail)
    f.map(_ shouldBe a[FailedResult])
  }

  private val emptyContent = EmailWithContent.txtOnlyWithoutSubject(Seq.empty, Seq.empty, "")

  "FailedHandler " should {
    "Always failed " in {
      val mail = Mail(mailAddress, Seq(mailAddress), emptyContent)
      shouldFailed(mail)
    }

    "Not accepted from" in {
      val mail = Mail(mailAddress.copy(domain = notAcceptedDomain), Seq(mailAddress), emptyContent)
      shouldFailed(mail)
    }

    "Not accepted to" in {
      val mail = Mail(mailAddress, Seq(mailAddress.copy(user = userUnknown)), emptyContent)
      shouldFailed(mail)
    }

    "Wrong address " in {
      val mail       = Mail(mailAddress, Seq(mailAddress), emptyContent)
      val failClient = createClientActor(SocketAddress(host, 2222))
      failClient.sendMail(mail).map(_ => fail()).recover { case exp =>
        logger.warn("", exp)
        succeed
      }
    }

    "No address " in {
      for {
        s <- clientWithActor.sendMail(Mail.empty).map {
          case FailedResult(ex) =>
            logger.debug("", ex)
            succeed
          case SuccessResult =>
            fail()
        }
      } yield {
        s
      }
    }

  }

}
