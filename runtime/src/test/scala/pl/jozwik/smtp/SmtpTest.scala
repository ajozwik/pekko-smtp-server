package pl.jozwik.smtp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.server.StreamServerRunner
import pl.jozwik.smtp.server.ServerOpts
import pl.jozwik.smtp.server.consumer.{Consumer, LogConsumer}
import pl.jozwik.smtp.util.{RuntimeConstants, ScalaAppWithLogger, TestUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class SmtpTest(port: Int, tlsOpts: TlsOpts = TlsOpts.fromSystemProps) extends Demo {

  private val serverOpts                   = ServerOpts[Consumer](port, 2048, LogConsumer.consumer)
  private implicit val system: ActorSystem = ActorSystem(s"SMTP-${serverOpts.port}")
  private val run                          = new StreamServerRunner((host, port) => Tcp().bind(host, port))(serverOpts, Option(tlsOpts))

  def runDemo(): Future[Unit] = {
    val objectMethods = classOf[Object].getMethods.map(_.getName).toSet
    RuntimeConstants.getClass.getMethods.filter(_.getReturnType == classOf[String]).filterNot(m => objectMethods.contains(m.getName)).foreach { f =>
      if (f.getGenericParameterTypes.length == 0)
        println(s"""${f.invoke(RuntimeConstants)}
             |""".stripMargin)
    }
    TestUtils.waitFor(!run.isBound, 10.millis)

    val futures = (1 to 1).map(i => Future(sendMail(s"client$i")(port)))

    Future.sequence(futures).map { _ =>
      run.close()
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
