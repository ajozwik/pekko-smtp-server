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

class UtilsFailedSpec extends AbstractSpecScalaCheck {

  import Utils._
  import Constants._

  "UtilsFailedSpec " should {

    "Unbalanced brackets <<" in {
      toMailAddress("<aa<a@pl>") shouldBe Symbol("left")
    }

    "Unbalanced brackets >>" in {
      toMailAddress("<aa>a@pl>") shouldBe Symbol("left")

    }

    "Unbalanced bracket >" in {
      toMailAddress("aa>a@pl") shouldBe Symbol("left")
    }

    "Non domain address" in {
      val user = "ajozwik"
      Utils.toMailAddress(s"<$user >") shouldBe Symbol("left")
    }

    "Unbalanced bracket <" in {
      toMailAddress("aa<a@pl") shouldBe Symbol("left")
    }

    "Unbalanced brackets <" in {
      toMailAddress("<aaa@pl") shouldBe Symbol("left")
    }
    "Unbalanced brackets >" in {
      toMailAddress("aaa@pl>") shouldBe Symbol("left")
    }

    "Empty mail address " in {
      toMailAddress("") shouldBe Symbol("left")
    }

    "Empty mail address in brackets" in {
      toMailAddress("<>") shouldBe Symbol("left")
    }

    "Unbalanced brackets << without parameter" in {
      extractAddressAndParameters("<ajozw<ik@ok.pl>") shouldBe Symbol("left")
    }

    "Unbalanced brackets >> without parameter" in {
      extractAddressAndParameters("<ajozw>ik@ok.pl>") shouldBe Symbol("left")
    }

    "Unbalanced bracket < without parameter" in {
      extractAddressAndParameters("<ajozwik@ok.pl") shouldBe Symbol("left")
    }

    "Unbalanced bracket > without parameter" in {
      extractAddressAndParameters("ajozwik@ok.pl>") shouldBe Symbol("left")
    }

    "No space" in {
      val either = extractAddressAndParameters("<ajozwik@ok.pl>SIZE=3")
      either shouldBe Symbol("left")
      either match {
        case Left(error) =>
          error should startWith(s"$SYNTAX_ERROR")
        case _ =>
          fail()
      }
    }
  }

}