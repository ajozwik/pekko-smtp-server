package pl.jozwik.smtp.client

import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.*

import scala.concurrent.Future

object StreamSmtpClient extends App with StrictLogging {

  private implicit val system: ActorSystem = ActorSystem("Client")

  private val port = 25

  private val WAIT_MILLIS = 1000

  private val address = "mail2.dotsystems.pl"

  private val serverAddress = SocketAddress(address, port)
  private val fromAddress   = MailAddress("ajozwik", "dotsystems.pl")
  private val mailAddress   = MailAddress("andrzej.jozwik", "gmail.com")
  private val mail          = Mail(fromAddress, Seq(mailAddress), EmailWithContent.txtOnly(Seq.empty, Seq.empty, "My Subject", "Content"))

  import system.dispatcher

  val client = new StreamClient(serverAddress)

  val futures = (1 to 1).map { _ =>
    TimeUnit.MILLISECONDS.sleep(WAIT_MILLIS)
    client.sendMail(mail).recover { case e =>
      logger.error("", e)
      FailedResult(e.getMessage)
    }
  }

  Future.sequence(futures).foreach { seq =>
    logger.debug(s"${seq.size} $seq")
    seq.foreach { result =>
      logger.debug(s"Result:$result")
    }
    system.terminate()
  }

}
