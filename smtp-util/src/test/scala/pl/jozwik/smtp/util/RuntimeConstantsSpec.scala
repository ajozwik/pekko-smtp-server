package pl.jozwik.smtp.util

class RuntimeConstantsSpec extends AbstractSpec {

  "Runtime constants" should {
    "Iterate by all values " in
      RuntimeConstants.getClass.getMethods.filter(_.getReturnType == classOf[RuntimeConstant]).foreach { f =>
        f.invoke(RuntimeConstants) match {
          case r: RuntimeConstant =>
            logger.trace(s"* ${r.asString}")
          case _ =>
            fail()
        }
      }

    "Return None" in {
      RuntimeConstant("_ _", "").propOrNone shouldBe None
    }
  }

}
