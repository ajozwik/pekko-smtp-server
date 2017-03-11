/*
 * Copyright (c) 2017 Andrzej Jozwik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pl.jozwik.smtp.server

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream
import akka.stream.stage._
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.command.MessageHandler
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util._

import scala.concurrent.Future
import scala.concurrent.duration._

object SmtpTimerGraphStageLogic {

  private val IMMEDIATELY = 1.nano
}

class SmtpTimerGraphStageLogic(
  shape: stream.Shape,
  in: stream.Inlet[String],
  out: stream.Outlet[String],
  addressHandler: AddressHandler,
  sizeHandler: SizeParameterHandler,
  localHostName: String,
  remote: InetSocketAddress,
  consumer: Mail => Future[ConsumedResult],
  readTimeout: FiniteDuration
)(implicit
  system: ActorSystem,
  timeout: Timeout)
    extends TimerGraphStageLogic(shape) with StrictLogging {

  import SmtpTimerGraphStageLogic._

  private def pushWithEndOfLine(message: String) = {
    push(out, Utils.withEndOfLine(message))
  }

  private def pullAndResetTimer(): Unit = {
    pull(in)
    scheduleOnce(TickTimeout, readTimeout)
  }

  override protected def onTimer(tk: Any): Unit = tk match {
    case SuccessfulConsumed =>
      pushWithEndOfLine(Constants.SMTP_OK)
    case FailedConsumed(error) =>
      logger.error(s"$error")
      pushWithEndOfLine(s"$TRANSACTION_FAILED $error")
    case `TickTimeout` =>
      pushWithEndOfLine(Errors.serviceNotAvailable(localHostName, readTimeout.toSeconds))
      failStage(new RuntimeException("Service timeout"))

  }

  private var accumulator = MailAccumulator.empty

  private val messageHandler = MessageHandler(addressHandler, sizeHandler, localHostName, remote, mail => {
    import system.dispatcher
    consumer(mail).foreach {
      response => scheduleOnce(response, IMMEDIATELY)
    }
  })

  setHandler(in, new InHandler {

    override def onPush(): Unit = {
      val line = grab[String](in)
      val stripped = line.stripLineEnd
      val (acc, response) = messageHandler.handleMessage(line, stripped, accumulator)

      response match {
        case TextResponse(command) =>
          pushWithEndOfLine(command)
        case NoDataResponse =>
          pullAndResetTimer()
        case QuitResponse(command) =>
          pushWithEndOfLine(command)
          completeStage()
        case MultiLineResponse(lines) =>
          push(out, lines.map(Utils.withEndOfLine).mkString)
        case NoResponse =>
      }
      accumulator = acc
    }
  })
  setHandler(out, new OutHandler {
    override def onPull(): Unit = {
      pullAndResetTimer()
    }
  })

  override def postStop(): Unit = {
    cancelTimer(TickTimeout)
    super.postStop()
  }
}

class SmtpGraphStage(addressHandler: AddressHandler, sizeHandler: SizeParameterHandler, localHostName: String,
  remote: InetSocketAddress, consumer: Mail => Future[ConsumedResult],
  readTimeout: FiniteDuration)(implicit
  system: ActorSystem,
  timeout: Timeout) extends GraphStage[stream.FlowShape[String, String]]
    with StrictLogging {

  private val in = stream.Inlet[String]("smtp.in")
  private val out = stream.Outlet[String]("smtp.out")

  override val shape = stream.FlowShape(in, out)

  override def createLogic(inheritedAttributes: stream.Attributes): GraphStageLogic =
    new SmtpTimerGraphStageLogic(shape, in, out, addressHandler, sizeHandler, localHostName, remote, consumer, readTimeout)

}