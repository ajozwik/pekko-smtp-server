package pl.jozwik.smtp.util

class UtilsFailedSpec extends AbstractSpec {

  import Utils.*

  "UtilsFailedSpec " should {

    "Unbalanced brackets <<" in {
      toMailAddress("<aa<a@pl>") should be(a[Left[?, ?]])
    }

    "Unbalanced brackets >>" in {
      toMailAddress("<aa>a@pl>") should be(a[Left[?, ?]])

    }

    "Unbalanced bracket >" in {
      toMailAddress("aa>a@pl") should be(a[Left[?, ?]])
    }

    "Non domain address" in {
      val user = "ajozwik"
      Utils.toMailAddress(s"<$user >") should be(a[Left[?, ?]])
    }

    "Unbalanced bracket <" in {
      toMailAddress("aa<a@pl") should be(a[Left[?, ?]])
    }

    "Unbalanced brackets <" in {
      toMailAddress("<aaa@pl") should be(a[Left[?, ?]])
    }
    "Unbalanced brackets >" in {
      toMailAddress("aaa@pl>") should be(a[Left[?, ?]])
    }

    "Empty mail address " in {
      toMailAddress("") should be(a[Left[?, ?]])
    }

    "Empty mail address in brackets" in {
      toMailAddress("<>") should be(a[Left[?, ?]])
    }

    "Unbalanced brackets << without parameter" in {
      extractAddressAndParameters("<ajozw<ik@ok.pl>") should be(a[Left[?, ?]])
    }

    "Unbalanced brackets >> without parameter" in {
      extractAddressAndParameters("<ajozw>ik@ok.pl>") should be(a[Left[?, ?]])
    }

    "Unbalanced bracket < without parameter" in {
      extractAddressAndParameters("<ajozwik@ok.pl") should be(a[Left[?, ?]])
    }

    "Unbalanced bracket > without parameter" in {
      extractAddressAndParameters("ajozwik@ok.pl>") should be(a[Left[?, ?]])
    }

    "No space" in {
      extractAddressAndParameters("<ajozwik@ok.pl>SIZE=3").left.value should startWith(s"${SmtpCodes.SYNTAX_ERROR}")
    }

    "Two @" in {
      extractAddressAndParameters("<ajozwik@ok.pl@aa.pl>") should be(a[Left[?, ?]])
    }

    "Two @ in toMailAddress" in {
      toMailAddress("aa@aa.pl@aa.pl") should be(a[Left[?, ?]])
    }

    "Two @ in toMailAddress in brackets" in {
      toMailAddress("<aa@aa.pl@aa.pl>") should be(a[Left[?, ?]])
    }
  }

}
