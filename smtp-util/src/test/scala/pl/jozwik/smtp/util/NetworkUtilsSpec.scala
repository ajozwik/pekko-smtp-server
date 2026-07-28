package pl.jozwik.smtp.util

class NetworkUtilsSpec extends AbstractSpec {
  import NetworkUtils.*

  "IOUtilsSpec" should {

    "localHostName" in {
      localHostName shouldBe a[String]
      hostnameFromEnvVariable("HOSTNAME") shouldBe a[String]
      hostnameFromEnvVariable("EE") shouldBe defaultHostName
    }

  }

}
