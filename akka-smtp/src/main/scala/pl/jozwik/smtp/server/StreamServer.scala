/*
 * Copyright (c) 2017 Andrzej Jozwik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pl.jozwik.smtp.server

import java.net.InetSocketAddress
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.{ NotUsed, stream }
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Constants.SERVICE_READY
import pl.jozwik.smtp.util.Utils.now
import pl.jozwik.smtp.util._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util._

object StreamServer extends StrictLogging {
  def apply(consumer: Mail => Future[ConsumedResult], configuration: Configuration,
    addressHandler: AddressHandler = NopAddressHandler)(implicit
    actorSystem: ActorSystem,
    materializer: Materializer): StreamServer =
    new StreamServer(consumer, configuration, addressHandler)

  private val address = "0.0.0.0"

}

class StreamServer private (
    consumer: Mail => Future[ConsumedResult],
    configuration: Configuration,
    addressHandler: AddressHandler)(implicit
    system: ActorSystem,
    materializer: Materializer)
  extends AutoCloseable with StrictLogging {

  import IOUtils._
  import StreamServer._

  private val sizeHandler = SizeParameterHandler(configuration.size)

  private val port = configuration.port

  private def handler(remote: InetSocketAddress, readTimeout: FiniteDuration) =
    new SmtpGraphStage(addressHandler, sizeHandler, localHostName, remote, consumer, readTimeout)

  private def serverLogic(conn: Tcp.IncomingConnection): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(now)
      val welcome = Source.single(ByteString(
        Utils.withEndOfLine(s"$SERVICE_READY $localHostName SMTP SERVER $date")))

      val logic = b.add(Flow[ByteString]
        .via(Framing.delimiter(ByteString(Constants.delimiter), Constants.maximumFrameLength, allowTruncation = true))
        .map(_.utf8String)
        .map { msg =>
          logger.debug(s"Server received: $msg")
          msg + Constants.delimiter
        }
        .via(handler(conn.remoteAddress, configuration.readTimeout))
        .map(ByteString.apply))

      val concat = b.add(Concat[ByteString]())
      welcome ~> concat.in(0)
      logic.outlet ~> concat.in(1)

      stream.FlowShape(logic.in, concat.out)
    })

  private val connectionHandler = Sink.foreach[Tcp.IncomingConnection] {
    conn =>
      logger.debug(s"Incoming connection from: ${conn.remoteAddress}")
      conn.handleWith(serverLogic(conn))
      ()
  }
  private val incomingConnections = Tcp().bind(address, port)
  private val binding = incomingConnections.to(connectionHandler).run()

  import system.dispatcher

  binding onComplete {
    case Success(b) =>
      logger.debug(s"Server started, listening on: ${b.localAddress}")
    case Failure(e) =>
      logger.error(s"Server could not be bound to $address:$port: ${e.getMessage}")
  }

  def close(): Unit = binding.foreach(_.unbind())
}