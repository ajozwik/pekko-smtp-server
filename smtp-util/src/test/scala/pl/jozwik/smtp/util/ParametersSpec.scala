package pl.jozwik.smtp.util

class ParametersSpec extends AbstractSpec {

  private val sizeHandler = SizeParameterHandler()

  "Parameters " should {
    "SizeParameterHandler do not parse wrong string" in {
      sizeHandler.validate("y") shouldBe Symbol("left")
    }
  }

}
