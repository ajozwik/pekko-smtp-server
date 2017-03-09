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

import java.util.concurrent.TimeoutException

import akka.pattern._
import org.scalatest.compatible.Assertion
import pl.jozwik.smtp.client.{FailedResult, MailWithAddress, SuccessResult}
import pl.jozwik.smtp.util.{EmailContent, Mail, Utils}

class SmtpIntegrationSpec extends AbstractSmtpSpec {

  "Smtp integration test" should {

    s"Wrong message" in {
      val f = clientRef ? "OK"
      f.recover {
        case x =>
          x shouldBe a[TimeoutException]
      }.mapTo[Assertion]
    }

    "finished without error" in {

      val mail = Mail(mailAddress, Seq(mailAddress), EmailContent.txtOnly("My Subject", "Content"))
      val future = clientRef ? MailWithAddress(mail, address)
      future.map { _ shouldBe SuccessResult }

    }

    "Too much data" in {
      val line = Utils.withEndOfLine("Content")
      val size = maxSize / line.length + 1
      logger.debug(s"$size $maxSize ${line.length}")
      val largeContent = Seq.fill(size)(line).mkString
      logger.debug(s"$size $maxSize ${line.length} ${largeContent.length}")
      val mail = Mail(mailAddress, Seq(mailAddress), EmailContent.txtOnly("My Subject", largeContent))
      val future = clientRef ? MailWithAddress(mail, address)
      future.map { result =>
        result shouldBe a[FailedResult]
      }

    }

  }

}