package pl.jozwik.smtp.client

import java.net.InetSocketAddress
import java.time.LocalDateTime
import akka.actor.Status.Failure
import akka.actor.{ ActorRef, PoisonPill, Props }
import akka.io.Tcp._
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import pl.jozwik.smtp.AkkaUtils.toWrite
import pl.jozwik.smtp.server.ActorWithTimeout
import pl.jozwik.smtp.util.{ Mail, SocketAddress }

import scala.concurrent.duration._

object ClientActorHandler {

  def props(senderRef: ActorRef, address: SocketAddress, mail: Mail, timeout: FiniteDuration): Props =
    Props(new ClientActorHandler(senderRef, address, mail, timeout))

  val FAIL_ON_ERROR = false
}

class ClientActorHandler(senderRef: ActorRef, address: SocketAddress, mail: Mail, val timeout: FiniteDuration) extends ActorWithTimeout {

  import ClientActorHandler._
  import pl.jozwik.smtp.util.Constants._

  private val manager = IO(Tcp)(context.system)

  override def preStart(): Unit = {
    val inetAddress = InetSocketAddress.createUnresolved(address.host, address.port)
    manager ! Connect(inetAddress)
    super.preStart()
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
        sender() ! Close
        context.self ! PoisonPill
        sys.error(s"$self Expected:$expectedCode received: $m")
      }
      Option(m)
    }

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

  override def unhandled(message: Any): Unit = message match {

    case CommandFailed(cmd) =>
      logger.error(s"$self $cmd failed")
      senderRef ! Failure(new IllegalStateException(s"$cmd failed"))
      self ! PoisonPill
    case _ =>
      super.unhandled(message)
  }

  def hello: Receive = { case Received(d) =>
    validateAndSendResponse(d, SERVICE_READY, s"$HELO ${mail.from.domain}")
    become(mailFrom)
  }

  def mailFrom: Receive = { case Received(d) =>
    validateAndSendResponse(d, REQUEST_COMPLETE, s"$MAIL_FROM:<${mail.from}>")
    become(rcptTo)
  }

  def rcptTo: Receive = { case Received(d) =>
    validateAndSendResponse(d, REQUEST_COMPLETE, s"$RCPT_TO:<${mail.to}>")
    become(data)
  }

  def data: Receive = { case Received(d) =>
    validateAndSendResponse(d, REQUEST_COMPLETE, s"$DATA")
    become(sendData)
  }

  def sendData: Receive = { case Received(d) =>
    validateAndSendResponse(d, START_MAIL_INPUT, s"""${mail.emailContent.bodyAsString}$crLf.""")
    become(quit)
  }

  def quit: Receive = { case Received(d) =>
    val success = validateAndSendResponse(d, REQUEST_COMPLETE, s"$QUIT", FAIL_ON_ERROR)
    become(close(success))
  }

  def close(failure: Option[String]): Receive = {
    case Received(d) =>
      validate(d, CLOSING_TERMINATION_CHANNEL)
      ()
    case x: ConnectionClosed =>
      logger.debug(s"$self $x")
      self ! PoisonPill
      val response = failure.map { error => FailedResult(error) }.getOrElse(SuccessResult)
      context.parent ! Counter(senderRef, response)
  }

  override protected def sendTimeoutMessage(lastAccess: LocalDateTime): Unit = {
    logger.debug(s"$self ${ActorWithTimeout.TIMEOUT}")
    context.parent ! Counter(senderRef, FailedResult(ActorWithTimeout.TIMEOUT))
    self ! PoisonPill
  }
}
