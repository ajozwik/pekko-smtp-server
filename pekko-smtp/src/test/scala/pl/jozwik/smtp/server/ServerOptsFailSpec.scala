package pl.jozwik.smtp.server

import pl.jozwik.smtp.util.{AbstractSpec, RuntimeConstants}

import scala.util.Properties

class ServerOptsFailSpec extends AbstractSpec {

  "ServerFail " should {
    "Fail init" in {
      ServerOpts.synchronized {
        Properties.propOrEmpty(RuntimeConstants.consumerClass.name)
        intercept[RuntimeException] {
          Properties.setProp(RuntimeConstants.consumerClass.name, classOf[String].getName)
          ServerOpts.fromSystemProps
        }
        Properties.clearProp(RuntimeConstants.consumerClass.name)
      }
    }
  }

}
