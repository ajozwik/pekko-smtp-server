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

import java.util.concurrent.TimeUnit

import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.TestUtils._
import concurrent.duration._

class SmtpServerTimeoutSpec extends AbstractSmtpSpec with SocketSpec {

  import scala.language.postfixOps

  val port = configuration.port

  override protected def readTimeout = (timeLimit / 2).min(1.second)

  override protected def afterAll() = {
    close()
    super.afterAll()
  }

  "SmtpServer " should {

    s"Handle $DATA ERROR" in {
      readAnswer(reader)
      writeLine(writer, s"$HELO")
      val probablyTimeout = readAnswer(reader)
      TimeUnit.MILLISECONDS.sleep(readTimeout.toMillis)
      val timeoutAnswer = readAnswer(reader)
      val notEmptyAnswer = if (timeoutAnswer.isEmpty) {
        probablyTimeout
      } else {
        timeoutAnswer
      }
      notEmptyAnswer should startWith(s"$SERVICE_NOT_AVAILABLE")
    }

  }

}