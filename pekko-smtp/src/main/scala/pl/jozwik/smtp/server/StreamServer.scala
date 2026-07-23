package pl.jozwik.smtp.server

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
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

class StreamServer private (
    listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]],
    address: String,
    port: Int
)(
    connectionHandler: => Sink[Tcp.IncomingConnection, Future[Done]]
)(implicit
    system: ActorSystem
) extends AutoCloseable
  with StrictLogging {

  logger.trace(s"PORT=$port")
  private lazy val binding: Future[Tcp.ServerBinding] = listenSource(address, port).to(connectionHandler).run()

  import system.dispatcher

  binding onComplete {
    case Success(b) =>
      logger.trace(s"Server started, listening on: ${b.localAddress}")
    case Failure(e) =>
      logger.error(s"Server could not be bound to $address:$port: ${e.getMessage}")
  }

  def isBound: Boolean = binding.isCompleted

  def close(): Unit = binding.foreach { b =>
    logger.trace(s"Server stopping, listening on: ${b.localAddress}")
    val f = b.unbind().flatMap(_ => b.whenUnbound)
    Await.result(f, 3.seconds)
    f.onComplete(_ => system.terminate())
    logger.trace(s"Server stopped, listening on: ${b.localAddress}")
  }

}
