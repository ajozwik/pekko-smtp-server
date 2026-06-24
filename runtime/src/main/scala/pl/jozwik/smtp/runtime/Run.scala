package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.{ Configuration, NopAddressHandler, StreamServer }
import pl.jozwik.smtp.server.consumer.Consumer

import scala.concurrent.duration.DurationInt

class Run[T <: Consumer](serverOpts: ServerOpts[T]) extends StrictLogging with AutoCloseable {

  private implicit val system: ActorSystem = ActorSystem(s"SMTP${serverOpts.port}") // Actor system

  private val configuration = Configuration(serverOpts.port, serverOpts.size, 2.minutes)

  lazy val server: StreamServer = StreamServer(serverOpts.consumer, configuration, NopAddressHandler) // NopAddressHandler - accepts all mail addresses

  def close(): Unit =
    server.close()

}
