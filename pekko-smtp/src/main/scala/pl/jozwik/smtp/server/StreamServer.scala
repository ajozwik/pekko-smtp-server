package pl.jozwik.smtp.server

import java.net.InetSocketAddress
import java.time.format.DateTimeFormatter
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString
import org.apache.pekko.{ NotUsed, stream }
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Utils.now
import pl.jozwik.smtp.util.*
import pl.jozwik.smtp.util.SmtpCodes.SERVICE_READY

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.*

object StreamServer extends StrictLogging {

  def apply(consumer: Mail => Future[ConsumedResult], configuration: Configuration, addressHandler: AddressHandler = NopAddressHandler)(implicit
      actorSystem: ActorSystem
  ): StreamServer =
    new StreamServer(consumer, configuration, addressHandler)

  private val address = "0.0.0.0"

}

class StreamServer private (consumer: Mail => Future[ConsumedResult], configuration: Configuration, addressHandler: AddressHandler)(implicit
    system: ActorSystem
) extends AutoCloseable
  with StrictLogging {

  import IOUtils.*
  import StreamServer.*

  private val sizeHandler = SizeParameterHandler(configuration.size)

  private val port = configuration.port
  logger.debug(s"PORT=$port")
  private val incomingConnections = Tcp().bind(address, port)
  private val binding             = incomingConnections.to(connectionHandler).run()

  import system.dispatcher

  binding onComplete {
    case Success(b) =>
      logger.debug(s"Server started, listening on: ${b.localAddress}")
    case Failure(e) =>
      logger.error(s"Server could not be bound to $address:$port: ${e.getMessage}")
  }

  private def handler(remote: InetSocketAddress, readTimeout: FiniteDuration, tls: AtomicBoolean): SmtpGraphStage =
    new SmtpGraphStage(addressHandler, sizeHandler, localHostName, remote, consumer, readTimeout, tls)

  private def serverLogic(remoteAddress: InetSocketAddress): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits.*
      val date    = DateTimeFormatter.RFC_1123_DATE_TIME.format(now)
      val welcome = Source.single(ByteString(Utils.withEndOfLine(s"$SERVICE_READY $localHostName SMTP SERVER $date")))
      val tls     = new AtomicBoolean(false)
      val logic   = b.add(
        Flow[ByteString]
          .via(Framing.delimiter(ByteString(Constants.Delimiter), Constants.MaximumFrameLength, allowTruncation = true))
          .map { msg =>
            val msgStr = msg.utf8String
            logger.debug(s"Server received: $msgStr")
            s"$msgStr${Constants.Delimiter}"
          }
          .via(handler(remoteAddress, configuration.readTimeout, tls))
          .map { msg =>
            logger.debug(s"Out: $msg")
            msg
          }
          .map(ByteString.apply)
      )

      val concat = b.add(Concat[ByteString]())
      welcome ~> concat.in(0)
      logic.outlet ~> concat.in(1)

      stream.FlowShape(logic.in, concat.out)
    })

  private lazy val connectionHandler = Sink.foreach[Tcp.IncomingConnection] { conn =>
    val remoteAddress = conn.remoteAddress
    logger.debug(s"Incoming connection from: $remoteAddress")
    conn.handleWith(serverLogic(remoteAddress))
    ()
  }

  def close(): Unit = binding.foreach(_.unbind())
}
