package pl.jozwik.smtp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.client.{StreamClient, SuccessResult}
import pl.jozwik.smtp.server.{AddressHandler, ConnectionHandler, NopAddressHandler, StreamServer}
import pl.jozwik.smtp.server.consumer.Consumer
import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.{Attachment, ConsumedResult, EmailWithContent, Mail, MailAddress, SocketAddress, SuccessfulConsumed, TestUtils}
import org.apache.pekko.util.ByteString
import scala.concurrent.Future
import scala.concurrent.duration.*

class UsageExamplesSpec extends AbstractWithActorSystemSpec with WithPort {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val _ = EphemeralTls.serverTlsOpts
    val _ = EphemeralTls.clientTlsOpts
  }

  private val targetHost = "localhost"

  "README examples" should {

    "work for Custom Consumer Implementation" in {
      class MyConsumer extends Consumer {
        override def consumer(mail: Mail): Future[ConsumedResult] = {
          Future.successful(SuccessfulConsumed)
        }
      }
      val myConsumer = new MyConsumer
      myConsumer.consumer(Mail(MailAddress("a", "b"), Seq.empty, EmailWithContent.txtOnly(Seq.empty, Seq.empty, "", ""))).map {
        _ shouldBe SuccessfulConsumed
      }
    }

    "work for Minimal SMTP Server and Client" in {
      val localSystem = ActorSystem("minimal-server")
      import localSystem.dispatcher
      val consumer = (mail: Mail) => {
        Future.successful(SuccessfulConsumed)
      }

      val maxSize = 1024 * 1024 // 1MB
      val readTimeout = 30.seconds
      val connectionHandler = ConnectionHandler.connectionHandler(maxSize, consumer, readTimeout, NopAddressHandler)()(localSystem)
      val p = TestUtils.notOccupiedPortNumber
      val server = StreamServer((h, port) => Tcp().bind(h, port), p)(connectionHandler)(localSystem)

      val address = SocketAddress(targetHost, p)
      val client = new StreamClient(address)(localSystem)

      val mail = Mail(
        from = MailAddress("sender", "example.com"),
        to = Seq(MailAddress("recipient", "example.com")),
        emailContent = EmailWithContent.txtOnly(Seq.empty, Seq.empty, "Subject", "Hello World!")
      )

      client.sendMail(mail).map { res =>
        server.close()
        res shouldBe SuccessResult
      }
    }

    "work for Custom Address Handler" in {
      class BlacklistAddressHandler(blacklist: Set[String]) extends AddressHandler {
        override def acceptFrom(from: MailAddress): Boolean = !blacklist.contains(from.domain)
        override def acceptTo(to: MailAddress): Boolean = true
      }
      
      val handler = new BlacklistAddressHandler(Set("spam.com"))
      handler.acceptFrom(MailAddress("user", "spam.com")) shouldBe false
      handler.acceptFrom(MailAddress("user", "example.com")) shouldBe true
    }

    "work for TLS Support" in {
      val localSystem = ActorSystem("tls-server")
      import localSystem.dispatcher
      val consumer = (mail: Mail) => Future.successful(SuccessfulConsumed)
      val maxSize = 1024 * 1024
      val readTimeout = 30.seconds

      // Using EphemeralTls for reliable test material
      val tlsOpts = EphemeralTls.serverTlsOpts

      val tlsConnectionHandler = ConnectionHandler.connectionHandler(
        maxSize,
        consumer,
        readTimeout,
        NopAddressHandler
      )(Some(tlsOpts))(localSystem)

      val p = TestUtils.notOccupiedPortNumber
      val server = StreamServer((h, port) => Tcp().bind(h, port), p)(tlsConnectionHandler)(localSystem)

      val address = SocketAddress(targetHost, p)
      // Using EphemeralTls.clientTlsOpts to match server's trust
      val tlsClient = new StreamClient(address, EphemeralTls.clientTlsOpts)(localSystem)

      val mail = Mail(
        from = MailAddress("sender", "example.com"),
        to = Seq(MailAddress("recipient", "example.com")),
        emailContent = EmailWithContent.txtOnly(Seq.empty, Seq.empty, "Subject", "Hello TLS!")
      )

      tlsClient.sendMail(mail).map { res =>
        server.close()
        res shouldBe SuccessResult
      }
    }

    "work for Sending Mail with Attachments" in {
       val mailWithAttachment = Mail(
        from = MailAddress("sender", "example.com"),
        to = Seq(MailAddress("recipient", "example.com")),
        emailContent = EmailWithContent(
          from = Seq(MailAddress("sender", "example.com")),
          to = Seq(MailAddress("recipient", "example.com")),
          subject = Some("Report"),
          txtBody = Some("Please find the report attached."),
          htmlBody = None,
          attachments = Seq(Attachment("report.pdf", ByteString("...pdf content...")))
        )
      )
      mailWithAttachment.emailContent.attachments should have size 1
      mailWithAttachment.emailContent.attachments.head.fileName shouldBe "report.pdf"
      mailWithAttachment.emailContent.attachments.head.content.utf8String shouldBe "...pdf content..."
    }
  }
}
