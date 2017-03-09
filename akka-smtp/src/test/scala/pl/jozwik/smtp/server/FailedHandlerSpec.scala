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

import akka.actor.Props
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.client.{FailedResult, MailWithAddress}
import pl.jozwik.smtp.util.{EmailContent, FailedConsumed, Mail, MailAddress}

import scala.concurrent.Await
import akka.pattern._

object FailedConsumerActor {
  def props: Props = Props[FailedConsumerActor]
}

class FailedConsumerActor extends AbstractActor with StrictLogging {

  def receive: Receive = {
    case mail: Mail =>
      logger.debug(s"Receive: $mail")
      sender() ! "Not supported"
      sender() ! FailedConsumed("Always failed")
  }
}

class FailedHandlerSpec extends AbstractSmtpSpec {
  private val notAcceptedDomain = "notaccepted"
  private val userUnknown = "userUnknown"

  override protected def consumerProps = FailedConsumerActor.props

  override protected def addressHandler = new AddressHandler {
    def acceptFrom(from: MailAddress): Boolean =
      from.domain != notAcceptedDomain

    def acceptTo(to: MailAddress): Boolean =
      to.user != userUnknown
  }

  private def shouldFailed[T](mail: Mail) = {
    val f = clientRef ? MailWithAddress(mail, address)
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