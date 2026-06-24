package pl.jozwik.smtp.util

class IOUtilsSpec extends AbstractSpec {
  import IOUtils.*

  "IOUtilsSpec" should {

    "localHostName" in {
      localHostName shouldBe a[String]
      hostnameFromEnvVariable("HOSTNAME") shouldBe a[String]
      hostnameFromEnvVariable("EE") shouldBe defaultHostName
    }

  }

}
