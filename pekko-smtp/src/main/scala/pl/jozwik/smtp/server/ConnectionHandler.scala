package pl.jozwik.smtp.server

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ TLSProtocol, scaladsl }
import org.apache.pekko.{ Done, NotUsed, stream }
import org.apache.pekko.stream.scaladsl.{ Concat, Flow, Framing, GraphDSL, Sink, Source, Tcp }
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.tls.{ SSLContextFactory, StartTlsBidiFlow }
import pl.jozwik.smtp.util.IOUtils.localHostName
import pl.jozwik.smtp.util.SmtpCodes.SERVICE_READY
import pl.jozwik.smtp.util.{ Constants, ConsumedResult, Mail, SizeParameterHandler, Utils }
import pl.jozwik.smtp.util.Utils.now

import java.net.InetSocketAddress
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLEngine
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object ConnectionHandler extends StrictLogging {

  private def dateNow                              = DateTimeFormatter.RFC_1123_DATE_TIME.format(now)
  private def welcome: Source[ByteString, NotUsed] = Source.single(ByteString(Utils.withEndOfLine(s"$SERVICE_READY $localHostName SMTP SERVER $dateNow")))

  def connectionHandler(addressHandler: AddressHandler, maxSize: Long, consumer: Mail => Future[ConsumedResult], readTimeout: FiniteDuration)(implicit
      actorSystem: ActorSystem
  ): Sink[Tcp.IncomingConnection, Future[Done]] =
    Sink.foreach[Tcp.IncomingConnection] { conn =>
      val remoteAddress = conn.remoteAddress
      logger.debug(s"Incoming connection from: $remoteAddress ${conn.localAddress}")
      val tls = new AtomicBoolean(false)
      conn
        .copy(flow = conn.flow.join(bidi(tls, SSLContextFactory.sslEngine()())))
        .handleWith(serverLogic(addressHandler, maxSize, consumer, readTimeout, tls)(remoteAddress))
      ()
    }

  private def bidi(tls: AtomicBoolean, createSSLEngine: () => SSLEngine): scaladsl.BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
    tlsWrapping.atop(StartTlsBidiFlow(tls, createSSLEngine)).reversed

  private val tlsWrapping: scaladsl.BidiFlow[ByteString, TLSProtocol.SendBytes, TLSProtocol.SslTlsInbound, ByteString, NotUsed] =
    scaladsl.BidiFlow.fromFlows(
      Flow[ByteString].map(TLSProtocol.SendBytes.apply),
      Flow[TLSProtocol.SslTlsInbound].collect { case sb: TLSProtocol.SessionBytes =>
        sb.bytes
      // ignore other kinds of inbounds (currently only Truncated)
      }
    )

  private def handler(addressHandler: AddressHandler, maxSize: Long, consumer: Mail => Future[ConsumedResult], readTimeout: FiniteDuration, tls: AtomicBoolean)(
      remote: InetSocketAddress
  )(implicit
      actorSystem: ActorSystem
  ): SmtpGraphStage = {
    val sizeHandler = SizeParameterHandler(maxSize)
    new SmtpGraphStage(addressHandler, sizeHandler, localHostName, consumer, readTimeout, tls)(remote)
  }

  private def serverLogic(
      addressHandler: AddressHandler,
      maxSize: Long,
      consumer: Mail => Future[ConsumedResult],
      readTimeout: FiniteDuration,
      tls: AtomicBoolean
  )(
      remoteAddress: InetSocketAddress
  )(implicit
      actorSystem: ActorSystem
  ): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits.*

      val logic = b.add(
        Flow[ByteString]
          .via(Framing.delimiter(ByteString(Constants.Delimiter), Constants.MaximumFrameLength, allowTruncation = true))
          .map { msg =>
            val msgStr = msg.utf8String
            logger.debug(s"Server received: $msgStr")
            s"$msgStr${Constants.Delimiter}"
          }
          .via(handler(addressHandler, maxSize, consumer, readTimeout, tls)(remoteAddress))
          .map { msg =>
            logger.debug(s"Out: $msg")
            msg
          }
          .map(ByteString.apply)
      )

      val concat = b.add(Concat[ByteString]())
      ConnectionHandler.welcome ~> concat.in(0)
      logic.outlet ~> concat.in(1)

      stream.FlowShape(logic.in, concat.out)
    })

}
