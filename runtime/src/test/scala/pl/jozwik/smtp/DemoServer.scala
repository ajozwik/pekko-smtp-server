package pl.jozwik.smtp

import pl.jozwik.smtp.util.{ScalaAppWithLogger, TestUtils}

import scala.concurrent.Future
import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class DemoServer(port: Int) extends Demo {

  private val run: ServerRunnable = new ServerRunnable(port)

  def startServer(): Unit = {
    val server = new Thread(run)
    server.start()
  }

  def runDemo(): Future[Unit] = {
    TestUtils.waitFor(!run.isBound, 10.millis)

    val futures = (1 to 1).map(i => Future(sendMail(s"client$i")(port)))

    Future.sequence(futures).map(_ => run.close()).recover { case e: Exception =>
      logger.error("Error:", e)
    }

  }

}

object DemoServer extends ScalaAppWithLogger {
  lazy val demo = new DemoServer(DemoHelper.Port)
  demo.startServer()

  demo.runDemo().onComplete { r =>
    logger.warn(s"All actions completed. Result: $r")
  }

}
