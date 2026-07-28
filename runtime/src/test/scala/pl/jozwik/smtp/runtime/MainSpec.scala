package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.{AbstractSpec, TestUtils}

import scala.concurrent.duration.DurationInt

class MainSpec extends AbstractSpec {

  // Main is a singleton object that reads TlsOpts.fromSystemProps on first access,
  // so the ephemeral keystore/truststore must be wired in before anything touches `Main`.
  System.setProperty("smtp.tls.keyStoreFile", EphemeralTls.serverKeyStoreFile.getAbsolutePath)
  System.setProperty("smtp.tls.trustStoreFile", EphemeralTls.trustStoreFile.getAbsolutePath)

  "Main " should {
    "start" in {
      Main.main(Array.empty)
      TestUtils.waitFor(!Main.r.isBound, 10.millis)
      Main.r.close()
    }
  }

}
