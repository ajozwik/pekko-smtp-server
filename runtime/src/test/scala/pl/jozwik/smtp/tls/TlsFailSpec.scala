package pl.jozwik.smtp.tls

import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.DemoHelper.{TlsVersion, keyStoreClientInputStream, trustStoreInputStream}
import pl.jozwik.smtp.server.consumer.{Consumer, LogConsumer}
import pl.jozwik.smtp.server.{ServerOpts, StreamServerRunner}
import pl.jozwik.smtp.util.{TestUtils, Utils}
import pl.jozwik.smtp.{AbstractWithActorSystemSpec, TlsOpts, WithClient, WithPort}

import scala.concurrent.duration.DurationInt
import scala.util.Using

class TlsFailSpec extends AbstractWithActorSystemSpec with WithClient with WithPort {
  private val tlsOpts    = EphemeralTls.serverTlsOpts
  private val serverOpts = ServerOpts[Consumer](port, 2048, LogConsumer.consumer, readTimeout = TestUtils.ReadTimeout)
  private val run        = new StreamServerRunner((host, port) => Tcp().bind(host, port))(serverOpts, Option(tlsOpts))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    TestUtils.waitFor(!run.isBound, 10.millis)
  }

  protected def createClient =
    new NioSslClient(TlsVersion, "localhost", port, "client", keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
      trustStoreInputStream,
      TlsOpts.trustPassword
    )

  "TlsServerFail " should {
    "Handle close connection" in {
      interceptAndPrint[RuntimeException] {
        Using.resource(
          createClient
        ) { implicit client =>
          client.writeAndWaitForRead("")
        }
      }
      succeed
    }

    "Handle close connection after handshake" in {
      interceptAndPrint[RuntimeException] {
        Using.resource(
          createClient
        ) { implicit client =>
          val ehloResponse = writeAndWaitForRead("EHLO test")
          logger.trace(s"${toMessage(ehloResponse)}")
          val tlsResponse = client.startTls()
          logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
        }
      }
      succeed
    }

    "Handle close connection after write" in {
      interceptAndPrint[RuntimeException] {
        Using.resource(
          createClient
        ) { implicit client =>
          client.writeMessage(Utils.withEndOfLine("EHLO test"))
        }
      }
      succeed
    }

    "Handle close connection after handshake write" in {
      interceptAndPrint[RuntimeException] {
        Using.resource(
          createClient
        ) { implicit client =>
          val ehloResponse = writeAndWaitForRead("EHLO test")
          logger.trace(s"${toMessage(ehloResponse)}")
          val tlsResponse = client.startTls()
          logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
          client.writeMessage(Utils.withEndOfLine("EHLO test"))
        }
      }
      succeed
    }
  }

}
