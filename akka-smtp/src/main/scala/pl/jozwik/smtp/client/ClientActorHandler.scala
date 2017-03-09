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
package pl.jozwik.smtp
package client

import java.net.InetSocketAddress

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import pl.jozwik.smtp.AkkaUtils._
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.{Mail, SocketAddress}

object ClientActorHandler {
  def props(senderRef: ActorRef, address: SocketAddress, mail: Mail): Props =
    Props(classOf[ClientActorHandler], senderRef, address, mail)

  val CONTINUE_ON_ERROR = false
}

class ClientActorHandler(senderRef: ActorRef, address: SocketAddress, mail: Mail)
    extends AbstractActor {

  import ClientActorHandler._

  private val manager = IO(Tcp)(context.system)

  override def preStart(): Unit = {
    val serverAddress = InetSocketAddress.createUnresolved(address.host, address.port)
    manager ! Connect(serverAddress)
  }

  private def send(message: String): Unit = {
    logger.debug(s"$message")
    sender() ! toWrite(message)
  }

  private def validateAndSendResponse(b: ByteString, expectedCode: Int, response: String, failOnError: Boolean = true): Option[String] = {
    val error = validate(b, expectedCode, failOnError)
    send(response)
    error
  }

  private def validate(b: ByteString, expectedCode: Int, failOnError: Boolean = true): Option[String] = {
    val m = b.utf8String
    logger.debug(s"$m")
    val expected = s"$expectedCode"
    if (m.take(3) != expected) {
      val error = s"Expected:$expectedCode received: $m"
      if (failOnError) {
        sender() ! Close
        context.self ! PoisonPill
        context.parent ! Counter(senderRef, FailedResult(error))
      }
      Option(error)
    } else {
      None
    }

  }

  override def unhandled(message: Any): Unit = message match {

    case CommandFailed(cmd) =>
      logger.error(s"$cmd failed")
      context.parent ! Counter(senderRef, FailedResult(s"$cmd failed"))
      self ! PoisonPill
  }

  def receive: Receive = {
    case Connected(_, _) =>
      val connection = sender()
      connection ! Register(self)
      become(hello)
  }

  def hello: Receive = {
    case Received(d) =>
      validateAndSendResponse(d, SERVICE_READY, s"$HELO localhost")
      become(mailFrom)
  }

  def mailFrom: Receive = {
    case Received(d) =>
      validateAndSendResponse(d, REQUEST_COMPLETE, s"$MAIL_FROM:<${mail.from}>")
      become(rcptTo)
  }

  def rcptTo: Receive = {
    case Received(d) =>
      mail.to.foreach(to => validateAndSendResponse(d, REQUEST_COMPLETE, s"$RCPT_TO:<$to>"))
      become(data)
  }

  def data: Receive = {
    case Received(d) =>
      validateAndSendResponse(d, REQUEST_COMPLETE, s"$DATA")
      become(sendData)
  }

  def sendData: Receive = {
    case Received(d) =>
      val sub = mail.emailContent.subject.map { s =>
        s"Subject:$s$endOfLine$endOfLine"
      } getOrElse {
        ""
      }
      validateAndSendResponse(d, START_MAIL_INPUT,
        s"""$sub${mail.emailContent.txtBody.getOrElse("")}$endOfLine.""")
      become(quit)
  }

  def quit: Receive = {
    case Received(d) =>
      val error = validateAndSendResponse(d, REQUEST_COMPLETE, s"$QUIT", CONTINUE_ON_ERROR)
      become(close(error))
  }

  def close(error: Option[String]): Receive = {
    case Received(d) =>
      validate(d, CLOSING_TERMINATION_CHANNEL)
      ()
    case x: ConnectionClosed =>
      logger.debug(s"$x")
      self ! PoisonPill
      context.parent ! Counter(senderRef, Result(error))
  }
}