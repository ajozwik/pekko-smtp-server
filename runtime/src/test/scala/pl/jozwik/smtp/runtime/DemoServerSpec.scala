package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.util.AbstractAsyncSpec
import pl.jozwik.smtp.{DemoServer, WithPort}

class DemoServerSpec extends AbstractAsyncSpec with WithPort {

  "ServerDemo " should {
    s"Client/Server demo" in {
      lazy val demo = new DemoServer(port, getClass.getSimpleName)
      demo.startServer()

      demo
        .runDemo()
        .map { b =>
          logger.warn("Demo finished")
          b shouldBe true
        }
        .recover { case e: Throwable =>
          logger.error(e.getMessage, e)
          fail(e)
        }
    }
  }

}
