package pl.jozwik.smtp

import pl.jozwik.smtp.util.{ScalaAppWithLogger, TestUtils}

import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class DemoServer(port: Int, testTag: String) extends Demo {

  private val run: ServerRunnable = new ServerRunnable(port, testTag)

  def startServer(): Unit = {
    val server = new Thread(run)
    server.start()
  }

  def runDemo(): Future[Boolean] = {
    TestUtils.waitFor(!run.isBound, 10.millis, s"${run.getClass.getName}")

    val futures = (1 to 1).map(i => Future(sendMail(tagged(s"client$i"))(port)))

    Future
      .sequence(futures)
      .map { _ =>
        run.close()
        true
      }
      .recover { case e: Exception =>
        logger.error("Error:", e)
        false
      }

  }

}

object DemoServer extends ScalaAppWithLogger {
  lazy val demo = new DemoServer(DemoHelper.Port, tagged("server"))
  demo.startServer()

  demo.runDemo().onComplete { r =>
    logger.warn(s"All actions completed. Result: $r")
  }

}
