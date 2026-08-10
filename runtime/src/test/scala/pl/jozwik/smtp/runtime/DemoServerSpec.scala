package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.util.{AbstractAsyncSpec, WithTagged}
import pl.jozwik.smtp.{DemoServer, WithPort}

class DemoServerSpec extends AbstractAsyncSpec with WithTagged with WithPort {

  "ServerDemo " should {
    s"Client/Server demo" in {
      lazy val demo = new DemoServer(port, tagged("server"))
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
