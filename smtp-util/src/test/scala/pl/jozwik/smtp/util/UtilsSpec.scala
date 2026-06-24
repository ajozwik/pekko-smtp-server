package pl.jozwik.smtp.util

import org.scalacheck.Gen.*
import org.scalacheck.Prop.*

class UtilsSpec extends AbstractSpecScalaCheck {

  import Utils.*

  "SmtpUtils " should {

    "Double brackets" in {
      val address = MailAddress("aa", "ok.pl").toString
      extractAddressAndParameters(s"<<$address>>") shouldBe Right((address, Map.empty))
    }

    "Extract address " in {
      val address = MailAddress("user", "domain.pl")
      extractAddressAndParameters(s"  < $address > ") match {
        case Right((ad, _)) if ad == address.toString =>
        case x: Any                                   =>
          fail(s"address $address only expected, received: $x")
      }
    }

    "Split line " in {
      check {
        forAll(listOfN(Constants.Four, alphaChar), alphaStr) { (chars, str) =>
          val command = new String(chars.toArray)
          val line    = s"$command:$str"
          val (c, p)  = Utils.splitLineByColon(line)
          c === command && str === p
        }

      }
    }

    "Address with spaces" in {
      val user   = "ajozwik"
      val domain = "jozwik.pl"
      toMailAddress(s" < $user@$domain >    ") shouldBe Right(MailAddress(user, domain))
    }

    "Extract user domain" in {
      check {
        forAll(alphaStr, listOf(alphaLowerChar)) { (user, d) =>
          val domain      = d.mkString
          val address     = s"$user@$domain"
          val mailAddress = Utils.toMailAddress(address)
          mailAddress match {
            case Right(_) =>
              user.nonEmpty && domain.nonEmpty
            case Left(_) =>
              !(user.nonEmpty && domain.nonEmpty)
          }
        }
      }
    }

  }

}
