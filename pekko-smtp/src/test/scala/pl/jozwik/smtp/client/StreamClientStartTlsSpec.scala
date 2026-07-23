package pl.jozwik.smtp.client

import org.apache.pekko.stream.scaladsl.Tcp
import org.scalatest.BeforeAndAfterAll
import pl.jozwik.smtp.server.{ServerOpts, StreamServerRunner}
import pl.jozwik.smtp.server.consumer.LogConsumer
import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.{AbstractAsyncSpec, EmailWithContent, Mail, TestUtils}
import pl.jozwik.smtp.{ActorSpec, WithPort}

import scala.concurrent.Await
import scala.concurrent.duration.*

class StreamClientStartTlsSpec extends AbstractAsyncSpec with BeforeAndAfterAll with ActorSpec with WithPort {

  private val serverTlsOpts = EphemeralTls.serverTlsOpts

  private val clientTlsOpts = EphemeralTls.clientTlsOpts

  private val run = new StreamServerRunner((host, port) => Tcp().bind(host, port))(ServerOpts(port, 10 * 1000, LogConsumer.consumer), Option(serverTlsOpts))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    while (!run.isBound) {
      TestUtils.sleep(10.millis)
    }
  }

  override protected def afterAll(): Unit = {
    run.close()
    Await.result(actorSystem.terminate(), timeout.duration)
  }

  "StreamClient with STARTTLS" should {

    "send a mail after upgrading the connection to TLS" in {
      val client = new StreamClient("localhost", port, Option(clientTlsOpts))
      val mail   = Mail(mailAddress, Seq(mailAddress), EmailWithContent.txtOnly(Seq.empty, Seq.empty, "My Subject", "Content"))
      client.sendMail(mail).map { result =>
        result shouldBe SuccessResult
      }
    }

  }

}
