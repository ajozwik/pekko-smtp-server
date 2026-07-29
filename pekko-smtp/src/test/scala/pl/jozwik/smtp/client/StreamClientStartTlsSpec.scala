package pl.jozwik.smtp.client

import pl.jozwik.smtp.server.SpecWithServer

import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.{EmailWithContent, Mail, SocketAddress}

class StreamClientStartTlsSpec extends SpecWithServer {

  private val clientTlsOpts = EphemeralTls.clientTlsOpts

  "StreamClient with STARTTLS" should {

    "run again" in {
      createServer.isBound shouldBe false
    }

    "send a mail after upgrading the connection to TLS" in {
      val client = new StreamClient(SocketAddress("localhost", port), clientTlsOpts)
      val mail   = Mail(mailAddress, Seq(mailAddress), EmailWithContent.txtOnly(Seq.empty, Seq.empty, "My Subject", "Content"))
      client.sendMail(mail).map { result =>
        result shouldBe SuccessResult
      }
    }

  }

}
