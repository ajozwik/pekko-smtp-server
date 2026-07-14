package pl.jozwik.smtp.util

class RuntimeConstantsSpec extends AbstractSpec {

  "Runtime constants" should {
    RuntimeConstants.getClass.getMethods.filter(_.getReturnType == classOf[RuntimeConstant]).foreach { f =>
      val r = f.invoke(RuntimeConstants).asInstanceOf[RuntimeConstant]
      logger.trace(s"* ${r.asString}")
    }
  }

}
