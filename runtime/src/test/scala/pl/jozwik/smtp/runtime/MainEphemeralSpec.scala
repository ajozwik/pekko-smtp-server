package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.util.RuntimeConstants

class MainEphemeralSpec extends AbstractMainSpec {

  "MainEphemeral " should {
    "start with ephemeral" in {
      runMain(
        Map(
          RuntimeConstants.ephemeral.name -> true.toString
        )
      )
    }
  }

}
