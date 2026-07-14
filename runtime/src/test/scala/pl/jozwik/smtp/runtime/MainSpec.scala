package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.util.{ AbstractSpec, TestUtils }

import scala.concurrent.duration.DurationInt

class MainSpec extends AbstractSpec {

  "Main " should {
    "start" in {
      Main.main(Array.empty)
      TestUtils.waitFor(!Main.r.isBound, 10.millis)
      Main.r.close()
    }
  }

}
