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

import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.{EmailContent, Mail, MailAddress, SocketAddress}

import scala.concurrent.Await
import scala.concurrent.duration._

object SmtpClient extends App with StrictLogging {

  val actorSystem = ActorSystem("Client")

  val executor = Executors.newSingleThreadScheduledExecutor()

  val ref = actorSystem.actorOf(ClientActor.props(), "ClientActor")
  private val number = 1
  private val TIMEOUT = 4 + number / 50
  private val PORT = 1587
  private val address = "localhost"

  (1 to number).foreach { i =>
    executor.schedule(new Runnable() {
      def run(): Unit = {
        val serverAddress = SocketAddress(address, PORT)
        val mailAddress = MailAddress("ajozwik", "tuxedo-wifi")
        val mail = Mail(mailAddress, Seq(mailAddress), EmailContent.txtOnly("My Subject", s"Content $i"))
        ref ! MailWithAddress(mail, serverAddress)
      }
    }, 20 * i, TimeUnit.MILLISECONDS)
  }

  import scala.language.postfixOps

  TimeUnit.SECONDS.sleep(TIMEOUT)

  Await.result(actorSystem.terminate(), TIMEOUT second)
  executor.shutdown()
}