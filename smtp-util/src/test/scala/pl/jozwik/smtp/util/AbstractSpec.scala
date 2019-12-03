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
package pl.jozwik.smtp.util

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.{ AsyncTimeLimitedTests, TimeLimitedTests }
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.wordspec.{ AnyWordSpecLike, AsyncWordSpecLike }
import org.scalatest.matchers.should.Matchers

trait AbstractSpecScalaCheck extends AbstractSpec with Checkers

trait Spec extends StrictLogging {
  val TIMEOUT_SECONDS                    = 600
  val timeLimit: Span                    = Span(TIMEOUT_SECONDS, Seconds)
  protected val mailAddress: MailAddress = MailAddress("ajozwik", "tuxedo-wifi")
}

trait AbstractSpec extends AnyWordSpecLike with TimeLimitedTests with Spec with Matchers

trait AbstractAsyncSpec extends AsyncWordSpecLike with AsyncTimeLimitedTests with Spec with Matchers
