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

import org.scalacheck.Gen._
import org.scalacheck.Prop._

class UtilsSpec extends AbstractSpecScalaCheck {

  import Utils._

  "SmtpUtils " should {

    "Double brackets" in {
      val address = MailAddress("aa", "ok.pl").toString
      extractAddressAndParameters(s"<<$address>>") shouldBe Right((address, Map.empty))
    }

    "Extract address " in {
      val address = MailAddress("user", "domain.pl")
      extractAddressAndParameters(s"  < $address > ") match {
        case Right((ad, _)) if ad == address.toString =>
        case x: Any =>
          fail(s"address $address only expected, received: $x")
      }
    }

    "Split line " in {
      check {
        forAll(listOfN(Constants.FOUR, alphaChar), alphaStr) {
          (chars, str) =>
            val command = new String(chars.toArray)
            val line = s"$command:$str"
            val (c, p) = Utils.splitLineByColon(line)
            c === command && str === p
        }

      }
    }

    "Address with spaces" in {
      val user = "ajozwik"
      val domain = "jozwik.pl"
      toMailAddress(s" < $user@$domain >    ") shouldBe Right(MailAddress(user, domain))
    }

    "Extract user domain" in {
      check {
        forAll(alphaStr, listOf(alphaLowerChar)) {
          (user, d) =>
            val domain = d.mkString
            val address = s"$user@$domain"
            val mailAddress = Utils.toMailAddress(address)
            mailAddress match {
              case Right(x) =>
                user.nonEmpty && domain.nonEmpty
              case Left(x) =>
                !(user.nonEmpty && domain.nonEmpty)
              case _ =>
                false
            }
        }
      }
    }

  }
}