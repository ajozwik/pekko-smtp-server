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
package pl.jozwik.smtp.client

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Framing, Source, Tcp}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.client.SmtpClient.{address, port}
import pl.jozwik.smtp.util._

import scala.concurrent.Future

object StreamSmtpClient extends App with StrictLogging {

  implicit val system = ActorSystem("Client")

  implicit val materializer = ActorMaterializer()

  private val port = 1587

  private val WAIT_MILLIS = 1000

  private val address = "localhost"

  val serverAddress = SocketAddress(address, port)
  val mailAddress = MailAddress("ajozwik", "tuxedo-wifi")
  val mail = Mail(mailAddress, Seq(mailAddress), EmailContent.txtOnly("My Subject", "Content"))
  import system.dispatcher
  val client = new StreamClient(address, port)
  val futures = (1 to 5).map {
    _ =>
      TimeUnit.MILLISECONDS.sleep(WAIT_MILLIS)
      client.sendMail(mail)
  }

  Future.sequence(futures).foreach {
    seq =>
      seq.foreach { result =>
        logger.debug(s"$result")
      }
      TimeUnit.MILLISECONDS.sleep(WAIT_MILLIS * 9)
      system.terminate()
  }

}