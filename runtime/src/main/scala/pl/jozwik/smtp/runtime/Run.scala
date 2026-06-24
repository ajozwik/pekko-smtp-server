package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.stream.scaladsl.{ Source, Tcp }
import pl.jozwik.smtp.server.{ ConnectionHandler, NopAddressHandler, StreamServer }
import pl.jozwik.smtp.server.consumer.Consumer

import scala.concurrent.Future

class Run[T <: Consumer](listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]])(serverOpts: ServerOpts[T])(implicit
    actorSystem: ActorSystem
) extends StrictLogging
  with AutoCloseable {

  private def connectionHandler =
    ConnectionHandler.connectionHandler(
      NopAddressHandler,
      serverOpts.maxSize,
      serverOpts.consumer,
      serverOpts.readTimeout
    ) // NopAddressHandler - accepts all mail addresses
  lazy val server: StreamServer = StreamServer(listenSource, serverOpts.port)(connectionHandler)

  def close(): Unit =
    server.close()

}
