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
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.TestUtils.*

import java.net.SocketException
import scala.concurrent.duration.*
import scala.util.{ Failure, Success }

class SmtpServerTimeoutSpec extends AbstractSmtpSpec with SocketSpec {

  lazy val port: Int = configuration.port

  override protected def readTimeout: FiniteDuration = (timeLimit / 2).min(1.second)

  override protected def afterAll(): Unit = {
    close()
    super.afterAll()
  }

  "SmtpServer " should {

    s"Handle $DATA ERROR" in {
      val a = readAnswer(reader)
      writeLine(writer, s"$HELO")
      val probablyTimeout = readAnswer(reader)
      TimeUnit.MILLISECONDS.sleep(readTimeout.toMillis)
      readAnswerOrError(reader) match {
        case Success(v) =>
          fail(v)
        case Failure(th) =>
          logger.error("", th)
      }
      probablyTimeout should startWith(s"$REQUEST_COMPLETE")
    }

  }

}
