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

import java.io.OutputStreamWriter
import java.net.{ InetSocketAddress, ServerSocket, Socket }

import pl.jozwik.smtp.server.FakeSmtpActor
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.TestUtils._
import pl.jozwik.smtp.util.Utils._
import pl.jozwik.smtp.util._

import scala.concurrent.Future

class SmtpClientFailSpec extends AbstractSmtpSpec {

  private val from = MailAddress("ajozwik", "aa")
  private val mail = Mail(from, Seq(from), EmailWithContent.txtOnlyWithoutSubject(Seq.empty, Seq.empty, ""))
  private val fakePort = notOccupiedPortNumber
  private val serverAddress = new InetSocketAddress(fakePort)
  private val fakeServerActor = actorSystem.actorOf(FakeSmtpActor.props(serverAddress))
  private val bufferSize = 4096

  "Client " should {
    "Restart " in {
      val failFuture = new StreamClient(address.host, notOccupiedPortNumber).sendMail(mail)
      failFuture.map(_ shouldBe a[FailedResult])
    }

    "Receive one " in {
      val successFuture = clientStream.sendMail(mail)
      successFuture.map(_ shouldBe SuccessResult)
    }

    "Handle wrong answer" in {
      val future = new StreamClient(address.host, fakePort).sendMail(mail)
      future.map(_ shouldBe a[FailedResult])
    }

    "Close connection " in {
      val socket = new Socket(serverAddress.getHostName, serverAddress.getPort)
      val writer = new OutputStreamWriter(socket.getOutputStream)
      writer.write(withEndOfLine(s"$HELO"))
      writer.flush()
      socket.close()
      socket.isClosed shouldBe true
    }

    "Expected codes not in response " in {
      val serverSocket = new ServerSocket(0)
      serverSocket.setReuseAddress(true)
      Future {
        val socket = serverSocket.accept()
        val writer = new OutputStreamWriter(socket.getOutputStream)
        val reader = socket.getInputStream
        val array = new Array[Byte](bufferSize)
        reader.read(array)
        logger.debug(s"${new String(array)}")
        writer.write(withEndOfLine(s"$SMTP_OK"))
        writer.flush()
        socket.close()
        socket.isClosed shouldBe true

      }
      val client = new StreamClient(serverAddress.getHostName, serverSocket.getLocalPort)
      val future = client.sendMail(mail)
      future.map { r =>

        r shouldBe a[FailedResult]
      }.recover {
        case e =>
          fail(e)
      }
    }

    "Unhandled " in {
      fakeServerActor ! "OK"
      2.shouldBe(2)
    }

  }

}