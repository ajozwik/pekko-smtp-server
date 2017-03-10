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
package server

import pl.jozwik.smtp.client.FailedResult
import pl.jozwik.smtp.util._

import scala.concurrent.Future

class FailedConsumerHandlerSpec extends FailedHandlerSpec {
  override protected def consumer(mail: Mail): Future[ConsumedResult] = Future.successful(FailedConsumed("Always failed"))
}

abstract class FailedHandlerSpec extends AbstractSmtpSpec {
  private val notAcceptedDomain = "notaccepted"
  private val userUnknown = "userUnknown"

  override protected def addressHandler = new AddressHandler {
    def acceptFrom(from: MailAddress): Boolean =
      from.domain != notAcceptedDomain

    def acceptTo(to: MailAddress): Boolean =
      to.user != userUnknown
  }

  private def shouldFailed[T](mail: Mail) = {
    val f = clientStream.sendMail(mail)
    f.map(_ shouldBe a[FailedResult])
  }

  private val emptyContent = EmailContent.txtOnlyWithoutSubject("")

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

  }
}