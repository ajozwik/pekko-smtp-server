package pl.jozwik.smtp.client

import java.net.InetSocketAddress
import java.time.LocalDateTime
import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.actor.{ ActorRef, PoisonPill, Props }
import org.apache.pekko.io.Tcp.*
import org.apache.pekko.io.{ IO, Tcp }
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.SmtpUtils.toWrite
import pl.jozwik.smtp.server.ActorWithTimeout
import pl.jozwik.smtp.util.{ Mail, SocketAddress }

import scala.concurrent.duration.*

object SenderActorHandler {

  def props(senderRef: ActorRef, address: SocketAddress, mail: Mail, timeout: FiniteDuration): Props =
    Props(new SenderActorHandler(senderRef, address, mail, timeout))

  private val FailOnError = false
}

class SenderActorHandler(senderRef: ActorRef, address: SocketAddress, mail: Mail, val timeout: FiniteDuration) extends ActorWithTimeout {

  import SenderActorHandler.*
  import pl.jozwik.smtp.util.Constants.*
  import pl.jozwik.smtp.util.SmtpCodes.*

  private val manager = IO(Tcp)(context.system)

  override def preStart(): Unit = {
    val inetAddress = InetSocketAddress.createUnresolved(address.host, address.port)
    manager ! Connect(inetAddress)
    super.preStart()
  }

  def receive: Receive = {
    case Connected(remote, local) =>
      val connection = sender()
      connection ! Register(self)
      logger.debug(s"$self Connected to remote -> $remote local ->  $local")
      become(hello)

    case x: ConnectionClosed =>
      logger.debug(s"$self $x")
      self ! PoisonPill
      context.parent ! Counter(senderRef, FailedResult(s"$x"))

    case CommandFailed(cmd) =>
      logger.error(s"$self $cmd failed")
      context.parent ! Counter(senderRef, FailedResult(s"${cmd.failureMessage}"))
      self ! PoisonPill
  }

  override protected def sendTimeoutMessage(lastAccess: LocalDateTime): Unit = {
    logger.debug(s"$self ${ActorWithTimeout.TIMEOUT}")
    context.parent ! Counter(senderRef, FailedResult(ActorWithTimeout.TIMEOUT))
    self ! PoisonPill
  }

  override def unhandled(message: Any): Unit = message match {

    case CommandFailed(cmd) =>
      logger.error(s"$self $cmd failed")
      senderRef ! Failure(new IllegalStateException(s"$cmd failed"))
      self ! PoisonPill
    case _ =>
      super.unhandled(message)
  }

  private def send(message: String): Unit = {
    logger.debug(s"$self $message")
    sender() ! toWrite(message)
  }

  private def validateAndSendResponse(b: ByteString, expectedCode: Int, response: String, failOnError: Boolean = true): Option[String] = {
    val failure = validate(b, expectedCode, failOnError)
    send(response)
    failure
  }

  private def validate(b: ByteString, expectedCode: Int, failOnError: Boolean = true): Option[String] = {
    val m = b.utf8String
    logger.debug(s"$self $m")
    val expected = s"$expectedCode"
    if (m.take(3) == expected) {
      None
    } else {
      if (failOnError) {
        context.parent ! ValidateError(senderRef, m)
        sender() ! Close
        context.self ! PoisonPill
        sys.error(s"$self Expected:$expectedCode received: $m")
      }
      Option(m)
    }

  }

  private def hello: Receive = { case Received(d) =>
    validateAndSendResponse(d, SERVICE_READY, s"$HELO ${mail.from.domain}")
    become(mailFrom)
  }

  private def mailFrom: Receive = { case Received(d) =>
    validateAndSendResponse(d, REQUEST_COMPLETE, s"$MAIL_FROM:<${mail.from}>")
    become(rcptTo)
  }

  private def rcptTo: Receive = { case Received(d) =>
    validateAndSendResponse(d, REQUEST_COMPLETE, s"$RCPT_TO:<${mail.to}>")
    become(data)
  }

  private def data: Receive = { case Received(d) =>
    validateAndSendResponse(d, REQUEST_COMPLETE, s"$DATA")
    become(sendData)
  }

  private def sendData: Receive = { case Received(d) =>
    validateAndSendResponse(d, START_MAIL_INPUT, s"""${mail.emailContent.bodyAsString}$CrLf.""")
    become(quit)
  }

  private def quit: Receive = { case Received(d) =>
    val success = validateAndSendResponse(d, REQUEST_COMPLETE, s"$QUIT", FailOnError)
    become(close(success))
  }

  private def close(failure: Option[String]): Receive = {
    case Received(d) =>
      validate(d, CLOSING_TERMINATION_CHANNEL)
      ()
    case x: ConnectionClosed =>
      logger.debug(s"$self $x")
      self ! PoisonPill
      val response: Result = failure match {
        case Some(error) =>
          FailedResult(error)
        case _ =>
          SuccessResult
      }
      context.parent ! Counter(senderRef, response)
  }

}
