package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.util.AbstractAsyncSpec
import pl.jozwik.smtp.{DemoServer, WithPort}

class DemoServerSpec extends AbstractAsyncSpec with WithPort {

  "ServerDemo " should {
    s"Client/Server demo" in {
      lazy val demo = new DemoServer(port)
      demo.startServer()

      demo
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
