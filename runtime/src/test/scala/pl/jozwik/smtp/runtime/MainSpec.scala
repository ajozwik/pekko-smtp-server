package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.RuntimeConstants

class MainSpec extends AbstractMainSpec {

  "Main " should {
    "start" in {
      runMain(
        Map(
          RuntimeConstants.keyStoreFile.name   -> EphemeralTls.serverKeyStoreFile.getAbsolutePath,
          RuntimeConstants.trustStoreFile.name -> EphemeralTls.trustStoreFile.getAbsolutePath
        )
      )

    }

  }

}
