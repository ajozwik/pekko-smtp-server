package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.{ Demo, SmtpTest, WithPort }
import pl.jozwik.smtp.util.AbstractAsyncSpec

class TlsServerSpec extends AbstractAsyncSpec with Demo with WithPort {

  "TlsServer " should {
    s"Call Demo" in {
      logger.warn(s"Starting demo $timeLimit")
      val test = new SmtpTest(port)
      test
        .runDemo()
        .map { _ =>
          logger.warn("Demo finished")
          succeed
        }
        .recover { case e: Throwable =>
          logger.error(e.getMessage, e)
          fail(e)
        }
    }
  }

}
