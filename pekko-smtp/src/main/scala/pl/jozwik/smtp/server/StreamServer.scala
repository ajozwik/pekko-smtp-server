package pl.jozwik.smtp.server

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done

import scala.concurrent.Future
import scala.util.*

object StreamServer {

  private val address = "0.0.0.0"

  def apply(listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]], port: Int)(
      connectionHandler: => Sink[Tcp.IncomingConnection, Future[Done]]
  )(implicit
      actorSystem: ActorSystem
  ): StreamServer =
    new StreamServer(listenSource, address, port)(connectionHandler)

}

class StreamServer private (listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]], address: String, port: Int)(
    connectionHandler: => Sink[Tcp.IncomingConnection, Future[Done]]
)(implicit
    system: ActorSystem
) extends AutoCloseable
  with StrictLogging {

  logger.debug(s"PORT=$port")
  private val binding = listenSource(address, port).to(connectionHandler).run()

  import system.dispatcher

  binding onComplete {
    case Success(b) =>
      logger.debug(s"Server started, listening on: ${b.localAddress}")
    case Failure(e) =>
      logger.error(s"Server could not be bound to $address:$port: ${e.getMessage}")
  }

  def close(): Unit = binding.foreach(_.unbind())
}
