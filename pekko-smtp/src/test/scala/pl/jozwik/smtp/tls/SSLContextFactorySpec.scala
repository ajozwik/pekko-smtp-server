package pl.jozwik.smtp.tls

import pl.jozwik.smtp.util.AbstractSpec

class SSLContextFactorySpec extends AbstractSpec {

  "SSLContextFactory" should {
    "Fail for wrong arguments" in {
      intercept[Exception] {
        SSLContextFactory.sslEngine("Fake")()()()
      }
    }
  }

}
