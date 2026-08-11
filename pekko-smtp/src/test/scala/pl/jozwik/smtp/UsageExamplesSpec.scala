package pl.jozwik.smtp

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Tcp
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.client.{StreamClient, SuccessResult}
import pl.jozwik.smtp.server.{AddressHandler, ConnectionHandler, NopAddressHandler, StreamServer}
import pl.jozwik.smtp.server.consumer.Consumer
import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.{Attachment, ConsumedResult, EmailWithContent, Mail, MailAddress, SocketAddress, SuccessfulConsumed, TestUtils}

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
      val consumer = (_: Mail) => {
        Future.successful(SuccessfulConsumed)
      }

      val maxSize                           = 1024 * 1024 // 1MB
      val readTimeout                       = 30.seconds
      def connectionHandler(whoIAm: String) = ConnectionHandler.connectionHandler(maxSize, consumer, readTimeout, whoIAm, NopAddressHandler)()(localSystem)
      val p                                 = TestUtils.notOccupiedPortNumber()
      val server                            = StreamServer((h, port) => Tcp().bind(h, port), p, tagged("server"))(connectionHandler)(localSystem)

      val address = SocketAddress(targetHost, p)
      val client  = new StreamClient(address, tagged("client"))(localSystem)

      val mail = Mail(
        from = MailAddress("sender", "example.com"),
        to = Seq(MailAddress("recipient", "example.com")),
        emailContent = EmailWithContent.txtOnly(Seq.empty, Seq.empty, "Subject", "Hello World!")
      )

      client.sendMail(mail).map { res =>
        server.close()
        localSystem.terminate()
        res shouldBe SuccessResult
      }
    }

    "work for Custom Address Handler" in {
      class BlacklistAddressHandler(blacklist: Set[String]) extends AddressHandler {
        override def acceptFrom(from: MailAddress): Boolean = !blacklist.contains(from.domain)
        override def acceptTo(to: MailAddress): Boolean     = true
      }

      val handler = new BlacklistAddressHandler(Set("spam.com"))
      handler.acceptFrom(MailAddress("user", "spam.com")) shouldBe false
      handler.acceptFrom(MailAddress("user", "example.com")) shouldBe true
    }

    "work for TLS Support" in {

      val consumer    = (_: Mail) => Future.successful(SuccessfulConsumed)
      val maxSize     = 1024 * 1024
      val readTimeout = 30.seconds

      val tlsOpts = EphemeralTls.serverTlsOpts

      def tlsConnectionHandler(whoIAm: String) = ConnectionHandler.connectionHandler(
        maxSize,
        consumer,
        readTimeout,
        whoIAm,
        NopAddressHandler
      )(Some(tlsOpts))

      val p      = TestUtils.notOccupiedPortNumber()
      val server = StreamServer((h, port) => Tcp().bind(h, port), p, tagged("server"))(tlsConnectionHandler)

      val address   = SocketAddress(targetHost, p)
      val tlsClient = new StreamClient(address, tagged("client"), EphemeralTls.clientTlsOpts)
      val mail      = Mail(
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
      val pdfContent         = "...pdf content..."
      val fileName           = "report.pdf"
      val mailWithAttachment = Mail(
        from = MailAddress("sender", "example.com"),
        to = Seq(MailAddress("recipient", "example.com")),
        emailContent = EmailWithContent(
          from = Seq(MailAddress("sender", "example.com")),
          to = Seq(MailAddress("recipient", "example.com")),
          subject = Some("Report"),
          txtBody = Some("Please find the report attached."),
          htmlBody = None,
          attachments = Seq(Attachment(fileName, ByteString(pdfContent)))
        )
      )
      mailWithAttachment.emailContent.attachments should have size 1
      mailWithAttachment.emailContent.attachments.head.fileName shouldBe fileName
      mailWithAttachment.emailContent.attachments.head.content.utf8String shouldBe pdfContent
    }
  }

}
