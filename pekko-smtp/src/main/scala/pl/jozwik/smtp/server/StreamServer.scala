package pl.jozwik.smtp.server

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.*
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.Done
import org.apache.pekko.util.ByteString

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.util.*

object StreamServer {

  private val address             = "0.0.0.0"
  private val defaultCloseTimeout = 3.seconds

  def apply(listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]], port: Int, whoIAm: String)(
      connectionHandler: String => Sink[Tcp.IncomingConnection, Future[Done]]
  )(implicit
      actorSystem: ActorSystem
  ): StreamServer =
    new StreamServer(listenSource, address, port, whoIAm)(connectionHandler(whoIAm))

}

class StreamServer private (
    listenSource: (String, Int) => Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]],
    address: String,
    port: Int,
    whoIAm: String
)(
    connectionHandler: => Sink[Tcp.IncomingConnection, Future[Done]]
)(implicit
    system: ActorSystem
) extends AutoCloseable
  with StrictLogging {

  private def prefixed(msg: String): String = s"$whoIAm $msg".trim

  logger.trace(prefixed(s"PORT=$port"))

  /** One switch shared by every accepted connection. Shutting it down completes the streams of the connections that are still open, instead of leaving them to
    * die together with the materializer (AbruptStageTerminationException).
    */
  private val killSwitch = KillSwitches.shared(s"smtp-server-$port")

  private lazy val binding: Future[Tcp.ServerBinding] =
    listenSource(address, port)
      .map { conn =>
        val cancellable = killSwitch.flow[ByteString]
        conn.copy(flow = Flow[ByteString].via(cancellable).via(conn.flow).via(cancellable))
      }
      .to(connectionHandler)
      .run()

  import system.dispatcher

  binding onComplete {
    case Success(b) =>
      logger.trace(prefixed(s"Server started, listening on: ${b.localAddress}"))
    case Failure(e) =>
      logger.error(prefixed(s"Server could not be bound to $address:$port: ${e.getMessage}"))
  }

  def isBound: Boolean = binding.isCompleted

  /** Stops accepting new connections and completes the connections that are still open. The `ActorSystem` is left running - it is not owned by the server, so
    * whoever created it decides when to terminate it.
    */
  def closeAsync(): Future[Done] = binding.transformWith {
    case Success(b) =>
      logger.trace(prefixed(s"Server stopping, listening on: ${b.localAddress}"))
      for {
        _    <- b.unbind()
        done <- b.whenUnbound
      } yield {
        logger.trace(prefixed(s"Server stopped, listening on: ${b.localAddress}"))
        closeStreams(done)
      }
    case Failure(e) =>
      logger.trace(prefixed(""), e)
      Future.successful(closeStreams(Done))
  }

  def close(): Unit = close(StreamServer.defaultCloseTimeout)

  private def close(closeTimeout: FiniteDuration): Unit = Await.result(closeAsync(), closeTimeout)

  private def closeStreams(done: Done) = {
    killSwitch.shutdown()
    done
  }

}
