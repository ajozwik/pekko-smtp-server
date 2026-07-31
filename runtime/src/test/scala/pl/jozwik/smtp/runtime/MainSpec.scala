package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.{AbstractSpec, RuntimeConstants, TestUtils}

import scala.concurrent.duration.DurationInt

class MainSpec extends AbstractSpec {

  "Main " should {
    "start" in {
      System.setProperty("smtp.tls.keyStoreFile", EphemeralTls.serverKeyStoreFile.getAbsolutePath)
      System.setProperty("smtp.tls.trustStoreFile", EphemeralTls.trustStoreFile.getAbsolutePath)
      Main.main(Array.empty)
      TestUtils.waitFor(!Main.r.isBound, 10.millis)
      Main.r.close()
      System.clearProperty("smtp.tls.keyStoreFile")
      System.clearProperty("smtp.tls.trustStoreFile")
    }

    "start with ephemeral" in {
      System.setProperty(RuntimeConstants.ephemeral.name, true.toString)
      Main.main(Array.empty)
      TestUtils.waitFor(!Main.r.isBound, 10.millis)
      Main.r.close()
      System.clearProperty(RuntimeConstants.ephemeral.name)
    }
  }

}
