package pl.jozwik.smtp.server

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{TLSProtocol, scaladsl}
import org.apache.pekko.{Done, NotUsed, stream}
import org.apache.pekko.stream.scaladsl.{Concat, Flow, Framing, GraphDSL, Sink, Source, Tcp}
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.tls.{SSLContextFactory, StartTlsBidiFlow, TlsOpts}
import pl.jozwik.smtp.util.NetworkUtils.localHostName
import pl.jozwik.smtp.util.SmtpCodes.SERVICE_READY
import pl.jozwik.smtp.util.{Constants, ConsumedResult, Mail, SizeParameterHandler, Utils}
import pl.jozwik.smtp.util.Utils.now

import java.net.InetSocketAddress
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLEngine
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object ConnectionHandler extends StrictLogging {

  def connectionHandler(
      maxSize: Long,
      consumer: Mail => Future[ConsumedResult],
      readTimeout: FiniteDuration,
      whoIAm: String,
      addressHandler: AddressHandler = NopAddressHandler
  )(
      tlsOpts: Option[TlsOpts] = None
  )(implicit
      actorSystem: ActorSystem
  ): Sink[Tcp.IncomingConnection, Future[Done]] = {
    val prefix = s"$whoIAm "
    Sink.foreach[Tcp.IncomingConnection] { conn =>
      val remoteAddress = conn.remoteAddress
      logger.trace(s"${prefix}Incoming connection from: $remoteAddress ${conn.localAddress}")
      val flow                       = conn.flow
      val tls                        = new AtomicBoolean(false)
      val (newFlow, starttlsSupport) = tlsOpts match {
        case Some(opts) =>
          val engine = SSLContextFactory
            .sslEngine(opts.protocol)(opts.keyStoreInputStream.call(), opts.keystorePassword, opts.keyPassword)(
              opts.trustStoreInputStream.call(),
              opts.trustPassword
            )
          (flow.join(bidi(tls, engine)), true)
        case _ =>
          (flow, false)
      }

      val logic = serverLogic(addressHandler, maxSize, consumer, readTimeout, tls, starttlsSupport, prefix)(remoteAddress)
        .watchTermination() { (_, future) =>
          import actorSystem.dispatcher
          future.onComplete {
            case Success(_) =>
              logger.trace(s"${prefix}Connection closed from: $remoteAddress")
            case Failure(ex) =>
              logger.error(s"${prefix}Failed: $remoteAddress", ex)
          }
        }

      conn
        .copy(flow = newFlow)
        .handleWith(logic)
      ()
    }
  }

  private def dateNow = DateTimeFormatter.RFC_1123_DATE_TIME.format(now)

  private def welcome: Source[ByteString, NotUsed] =
    Source.single(ByteString(Utils.withEndOfLine(s"$SERVICE_READY $localHostName SMTP SERVER $dateNow")))

  private def bidi(
      tls: AtomicBoolean,
      createSSLEngine: () => SSLEngine
  ): scaladsl.BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
    tlsWrapping.atop(StartTlsBidiFlow(tls, createSSLEngine)).reversed

  private val tlsWrapping: scaladsl.BidiFlow[ByteString, TLSProtocol.SendBytes, TLSProtocol.SslTlsInbound, ByteString, NotUsed] =
    scaladsl.BidiFlow.fromFlows(
      Flow[ByteString].map(TLSProtocol.SendBytes.apply),
      Flow[TLSProtocol.SslTlsInbound].collect { case sb: TLSProtocol.SessionBytes =>
        sb.bytes
      }
    )

  private def handler(
      addressHandler: AddressHandler,
      maxSize: Long,
      consumer: Mail => Future[ConsumedResult],
      readTimeout: FiniteDuration,
      tls: AtomicBoolean,
      starttlsSupport: Boolean
  )(
      remote: InetSocketAddress
  )(implicit
      actorSystem: ActorSystem
  ): SmtpGraphStage = {
    val sizeHandler = SizeParameterHandler(maxSize)
    new SmtpGraphStage(addressHandler, sizeHandler, localHostName, consumer, readTimeout, tls, starttlsSupport)(remote)
  }

  private def serverLogic(
      addressHandler: AddressHandler,
      maxSize: Long,
      consumer: Mail => Future[ConsumedResult],
      readTimeout: FiniteDuration,
      tls: AtomicBoolean,
      starttlsSupport: Boolean,
      prefix: String
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
            val msgStr        = msg.utf8String
            val msgStrTrimmed = msgStr.trim
            logger.trace(s"$prefix${tls.get()} Smtp Server received: $msgStrTrimmed")
            s"$msgStr${Constants.Delimiter}"
          }
          .via(handler(addressHandler, maxSize, consumer, readTimeout, tls, starttlsSupport)(remoteAddress))
          .map { msg =>
            logger.trace(s"${prefix}Smtp Out: ${msg.trim}")
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
