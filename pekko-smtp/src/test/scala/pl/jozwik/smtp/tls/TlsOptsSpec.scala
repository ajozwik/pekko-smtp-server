package pl.jozwik.smtp.tls

import pl.jozwik.smtp.util.{AbstractSpec, RuntimeConstants}

import java.io.File
import scala.util.{Properties, Using}

class TlsOptsSpec extends AbstractSpec {

  "TlsOpts " should {
    val systemProps = TlsOpts.fromSystemProps
    "Fail if keys not set" in {
      intercept[RuntimeException] {
        systemProps.keyStoreInputStream.call()
      }
      intercept[RuntimeException] {
        systemProps.trustStoreInputStream.call()
      }
    }

    "Read default settings for file" in {
      val tmpFile = File.createTempFile("aaa", "bbb")
      tmpFile.deleteOnExit()
      Properties.setProp(RuntimeConstants.keyStoreFile.name, tmpFile.getAbsolutePath)
      Using.resource(systemProps.keyStoreInputStream.call()) { input =>
        Option(input) should not be empty
      }
      Properties.clearProp(RuntimeConstants.keyStoreFile.name)
    }

    "Fail if resource not exist" in {
      Properties.setProp(RuntimeConstants.keyStoreResource.name, "/fake")
      intercept[RuntimeException] {
        systemProps.keyStoreInputStream.call()
      }
    }
  }

}
