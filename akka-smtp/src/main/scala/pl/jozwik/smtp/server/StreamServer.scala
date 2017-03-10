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

import java.net.{InetAddress, InetSocketAddress}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}
import akka.{NotUsed, stream}
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.Supervisor
import pl.jozwik.smtp.util.Constants.SERVICE_READY
import pl.jozwik.smtp.util.Utils.now
import pl.jozwik.smtp.util._

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util._

object StreamServer extends StrictLogging {
  def apply(consumerProps: Props, configuration: Configuration,
    addressHandler: AddressHandler = NopAddressHandler)(implicit
    actorSystem: ActorSystem,
    materializer: Materializer): StreamServer =
    new StreamServer(consumerProps, configuration, addressHandler)

  private val hostname = System.getenv.get("HOSTNAME",System.getenv.get("COMPUTERNAME"))

  private val localHostName = Try(InetAddress.getLocalHost.getHostName) match {
    case Success(name) =>
      name
    case Failure(th) =>
      logger.error(th.getMessage, th)
      hostname
  }
  private val maximumFrameLength = 1024

  private val address = "0.0.0.0"

  implicit private final val timeout = Timeout(1, TimeUnit.SECONDS)

  private def createActorRef(
    supervisorRef: ActorRef,
    propsWithName: PropsWithName
  ): ActorRef = {
    val future = supervisorRef ? propsWithName
    Await.result(future.mapTo[ActorRef], timeout.duration)
  }
}

class StreamServer private (
  consumerProps: Props,
  configuration: Configuration,
  addressHandler: AddressHandler
)(implicit
  system: ActorSystem,
  materializer: Materializer)
    extends AutoCloseable with StrictLogging {
  import StreamServer._

  private val supervisorRef = system.actorOf(Supervisor.props)

  private val consumerRef = createActorRef(supervisorRef, PropsWithName(consumerProps, "Consumer"))

  private val sizeHandler = SizeParameterHandler(configuration.size)

  private val port = configuration.port

  private def handler(remote: InetSocketAddress, readTimeout: FiniteDuration) =
    new MyGraphStage(addressHandler, sizeHandler, localHostName, remote, consumerRef, readTimeout)

  private def serverLogic(conn: Tcp.IncomingConnection): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val welcome = Source.single(ByteString(
        Utils.withEndOfLine(s"$SERVICE_READY $localHostName SMTP SERVER ${
          DateTimeFormatter.RFC_1123_DATE_TIME.format(now)
        }")
      ))
      val logic = b.add(Flow[ByteString]
        .via(Framing.delimiter(ByteString(Constants.delimiter), maximumFrameLength, allowTruncation = true))
        .map(_.utf8String)
        .map { msg => logger.debug(s"Server received: $msg"); msg + Constants.delimiter }
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