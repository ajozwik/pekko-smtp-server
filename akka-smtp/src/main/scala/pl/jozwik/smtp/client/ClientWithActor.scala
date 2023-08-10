package pl.jozwik.smtp.client

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern._
import org.apache.pekko.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.{ Mail, SocketAddress }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

class ClientWithActor(address: SocketAddress)(implicit actorSystem: ActorSystem, timeout: FiniteDuration) extends SenderClient with StrictLogging {

  private val ref                 = actorSystem.actorOf(SenderActor.props())
  private implicit val t: Timeout = timeout

  def sendMail(mail: Mail): Future[Result] = {
    val future = ref ? MailWithAddress(mail, address)
    future.mapTo[Result]
  }
}
