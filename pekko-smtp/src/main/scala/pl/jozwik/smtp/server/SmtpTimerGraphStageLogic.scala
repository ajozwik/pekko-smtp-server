package pl.jozwik.smtp.server

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.stage.{ InHandler, OutHandler, TimerGraphStageLogic }

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import org.apache.pekko.stream.{ Inlet, Outlet }
import org.apache.pekko.stream
import pl.jozwik.smtp.server.command.MessageHandler
import pl.jozwik.smtp.util.SmtpCodes.*
import pl.jozwik.smtp.util.*
import java.net.InetSocketAddress
import scala.concurrent.duration.*
import scala.concurrent.Future

object SmtpTimerGraphStageLogic {

  private val IMMEDIATELY = 1.nano
}

class SmtpTimerGraphStageLogic(
    shape: stream.FlowShape[String, String],
    addressHandler: AddressHandler,
    sizeHandler: SizeParameterHandler,
    localHostName: String,
    remote: InetSocketAddress,
    consumer: Mail => Future[ConsumedResult],
    readTimeout: FiniteDuration,
    tls: AtomicBoolean
)(implicit system: ActorSystem)
  extends TimerGraphStageLogic(shape)
  with StrictLogging {

  private val accumulator = new AtomicReference(MailAccumulator.empty)

  private val messageHandler = MessageHandler(
    addressHandler,
    sizeHandler,
    localHostName,
    remote,
    mail => {
      import system.dispatcher
      consumer(mail).foreach { response =>
        scheduleOnce(response, SmtpTimerGraphStageLogic.IMMEDIATELY)
      }
    }
  )

  setHandler(shape.in, inHandler(shape.in, shape.out))
  setHandler(shape.out, outHandler(shape.in))

  override def postStop(): Unit = {
    cancelTimer(TickTimeout)
    super.postStop()
  }

  override protected def onTimer(tk: Any): Unit =
    handleTimeout(tk)(shape.out)

  private def inHandler(implicit in: Inlet[String], out: Outlet[String]) =
    new InHandler {

      override def onPush(): Unit = {
        val line            = grab[String](in)
        val stripped        = line.stripLineEnd
        val (acc, response) = messageHandler.handleMessage(line, stripped, accumulator.get())

        handleResponse(response)
        accumulator.set(acc)
        tls.set(acc.tls)
      }
    }

  private def outHandler(in: Inlet[String]) =
    new OutHandler {

      override def onPull(): Unit = {
        pullAndResetTimer(in)
      }
    }

  private def handleTimeout(tk: Any)(implicit out: Outlet[String]): Unit =
    tk match {
      case SuccessfulConsumed =>
        pushWithEndOfLine(SmtpResponses.SMTP_OK)
      case FailedConsumed(error) =>
        logger.error(s"$error")
        pushWithEndOfLine(s"$TRANSACTION_FAILED $error")
      case `TickTimeout` =>
        pushWithEndOfLine(Errors.serviceNotAvailable(localHostName, readTimeout.toSeconds))
        failStage(new RuntimeException("Service timeout"))
      case x =>
        failStage(new UnsupportedOperationException(s"$x"))
    }

  private def handleResponse(response: ResponseMessage)(implicit in: Inlet[String], out: Outlet[String]): Unit = {
    response match {
      case TextResponse(command) =>
        pushWithEndOfLine(command)
      case NoDataResponse =>
        pullAndResetTimer
      case QuitResponse(command) =>
        pushWithEndOfLine(command)
        completeStage()
      case MultiLineResponse(lines) =>
        push(out, lines.map(Utils.withEndOfLine).mkString)
      case NoResponse =>
    }
  }

  private def pushWithEndOfLine(message: String)(implicit out: Outlet[String]): Unit =
    push(out, Utils.withEndOfLine(message))

  private def pullAndResetTimer(implicit in: Inlet[String]): Unit = {
    pull(in)
    scheduleOnce(TickTimeout, readTimeout)
  }

}
