package pl.jozwik.smtp
package client

import java.io.OutputStreamWriter
import java.net.{ InetSocketAddress, ServerSocket, Socket }

import pl.jozwik.smtp.server.FakeSmtpActor
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.TestUtils.*
import pl.jozwik.smtp.util.SmtpResponses.*
import pl.jozwik.smtp.util.Utils.*
import pl.jozwik.smtp.util.*

import scala.concurrent.Future

class SmtpClientFailSpec extends AbstractSmtpSpec {

  private val from            = MailAddress("ajozwik", "aa")
  private val mail            = Mail(from, Seq(from), EmailWithContent.txtOnlyWithoutSubject(Seq.empty, Seq.empty, ""))
  private val fakePort        = notOccupiedPortNumber
  private val serverAddress   = new InetSocketAddress(fakePort)
  private val fakeServerActor = actorSystem.actorOf(FakeSmtpActor.props(serverAddress))
  private val bufferSize      = 4096

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
        val array  = new Array[Byte](bufferSize)
        reader.read(array)
        logger.debug(s"${new String(array)}")
        writer.write(withEndOfLine(s"$SMTP_OK"))
        writer.flush()
        socket.close()
        socket.isClosed shouldBe true

      }
      val client = new StreamClient(serverAddress.getHostName, serverSocket.getLocalPort)
      val future = client.sendMail(mail)
      future
        .map { r =>
          r shouldBe a[FailedResult]
        }
        .recover { case e: Exception =>
          fail(e)
        }
    }

    "Unhandled " in {
      fakeServerActor ! "OK"
      2 shouldBe 2
    }

  }

}
