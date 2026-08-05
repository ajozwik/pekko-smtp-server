package pl.jozwik.smtp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.server.StreamServerRunner
import pl.jozwik.smtp.server.ServerOpts
import pl.jozwik.smtp.server.consumer.{Consumer, LogConsumer}
import pl.jozwik.smtp.tls.TlsOpts
import pl.jozwik.smtp.util.{RuntimeConstants, ScalaAppWithLogger, TestUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class SmtpTest(port: Int, tlsOpts: TlsOpts = TlsOpts.fromSystemProps, testTag: String = "") extends Demo {

  private def tagged(role: String): String = if (testTag.isEmpty) role else s"$role[$testTag]"
  private val systemName                   = if (testTag.isEmpty) "SMTP" else s"SMTP-$testTag"

  private val serverOpts                   = ServerOpts[Consumer](port, 2048, LogConsumer.consumer, readTimeout = TestUtils.ReadTimeout)
  private implicit val system: ActorSystem = ActorSystem(s"$systemName-${serverOpts.port}")
  private val run                          = new StreamServerRunner((host, port) => Tcp().bind(host, port))(serverOpts, Option(tlsOpts), tagged("server"))

  def runDemo(): Future[Unit] = {
    val objectMethods = classOf[Object].getMethods.map(_.getName).toSet
    RuntimeConstants.getClass.getMethods.filter(_.getReturnType == classOf[String]).filterNot(m => objectMethods.contains(m.getName)).foreach { f =>
      if (f.getGenericParameterTypes.length == 0)
        logger.trace(s"""${f.invoke(RuntimeConstants)}
             |""".stripMargin)
    }
    TestUtils.waitFor(!run.isBound, 10.millis, s"${run.getClass.getName}")

    val futures = (1 to 1).map(i => Future(sendMail(tagged(s"client$i"))(port)))

    Future.sequence(futures).flatMap { _ =>
      run.close()
      system.terminate().map(t => logger.trace(s"$t"))
    }

  }

}

object SmtpTest extends ScalaAppWithLogger {

  val demo = new SmtpTest(DemoHelper.Port)

  demo.runDemo().onComplete {
    case Success(r) =>
      logger.warn(s"All actions completed. Result: $r")
    case Failure(e) =>
      logger.error("Error:", e)
//    System.exit(0)
  }

}
