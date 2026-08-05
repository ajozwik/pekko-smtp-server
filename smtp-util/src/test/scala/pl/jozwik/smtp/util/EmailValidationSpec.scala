package pl.jozwik.smtp.util

class EmailValidationSpec extends AbstractSpec {
  import Utils.*

  "Email validation" should {
    "reject email with multiple @ symbols: aa@aa.pl@aa.pl" in {
      toMailAddress("aa@aa.pl@aa.pl") should be(a[Left[?, ?]])
    }

    "reject email with multiple @ symbols in brackets: <aa@aa.pl@aa.pl>" in {
      toMailAddress("<aa@aa.pl@aa.pl>") should be(a[Left[?, ?]])
    }

    "reject email with multiple @ symbols via extractAddressAndParameters: <aa@aa.pl@aa.pl>" in {
      extractAddressAndParameters("<aa@aa.pl@aa.pl>") should be(a[Left[?, ?]])
    }
  }

}
