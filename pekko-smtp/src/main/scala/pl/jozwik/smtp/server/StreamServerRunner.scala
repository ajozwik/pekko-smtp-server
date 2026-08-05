package pl.jozwik.smtp.server

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.{Sink, Source, Tcp}
import pl.jozwik.smtp.server.{ConnectionHandler, ServerOpts, StreamServer}
import pl.jozwik.smtp.server.consumer.Consumer
import pl.jozwik.smtp.tls.TlsOpts

import scala.concurrent.Future

class StreamServerRunner[T <: Consumer](listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]])(
    serverOpts: ServerOpts[T],
    tlsOpts: Option[TlsOpts] = None
)(implicit
    actorSystem: ActorSystem
) extends StrictLogging
  with AutoCloseable {

  private def connectionHandler: Sink[Tcp.IncomingConnection, Future[Done]] =
    ConnectionHandler.connectionHandler(serverOpts.maxSize, serverOpts.consumer, serverOpts.readTimeout)(
      tlsOpts
    ) // NopAddressHandler - accepts all mail addresses

  private lazy val server: StreamServer = StreamServer(listenSource, serverOpts.port)(connectionHandler)

  def close(): Unit = {
    logger.warn("Closing server")
    server.close()
  }

  /** Non blocking variant of [[close]]. The `ActorSystem` stays alive - terminate it yourself once this completes. */
  def closeAsync(): Future[Done] = {
    logger.warn("Closing server")
    server.closeAsync()
  }

  def isBound: Boolean = server.isBound

  def start(): Unit = server
}
