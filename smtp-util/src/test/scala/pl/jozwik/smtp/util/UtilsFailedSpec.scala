package pl.jozwik.smtp.util

class UtilsFailedSpec extends AbstractSpec {

  import Utils.*

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
          error should startWith(s"${SmtpCodes.SYNTAX_ERROR}")
        case _ =>
          fail()
      }
    }
  }

}
