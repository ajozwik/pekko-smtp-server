package pl.jozwik.smtp
package server

import java.net.InetSocketAddress
import org.apache.pekko.actor.Props
import org.apache.pekko.io.Tcp.*
import pl.jozwik.smtp.SmtpUtils.*
import pl.jozwik.smtp.util.SmtpCodes.SERVICE_READY

object FakeSmtpActor {
  def props(bindAddress: InetSocketAddress): Props = Props(new FakeSmtpActor(bindAddress))
}

class FakeSmtpActor(bindAddress: InetSocketAddress) extends AbstractSmtpActor(bindAddress) {

  def receive: Receive = {

    case Connected(_, _) =>
      sender() ! Register(self)
      sender() ! toWrite(s"$SERVICE_READY SMTP DEMO")

    case Received(data) =>
      val str = data.utf8String
      logger.debug(s"$str")
      sender() ! toWrite(s"ALA MA KOTA")
  }

}
