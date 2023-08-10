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
package pl.jozwik.smtp

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import pl.jozwik.smtp.client.{ ClientWithActor, StreamClient }
import pl.jozwik.smtp.server._
import pl.jozwik.smtp.server.consumer.LogConsumer
import pl.jozwik.smtp.util._

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

object ActorSpec {
  private val number = Iterator from 1
}

trait ActorSpec extends StrictLogging {

  protected implicit val actorSystem: ActorSystem = ActorSystem(s"test-${ActorSpec.number.next()}", ConfigFactory.parseResources("application-test.conf"))

  private val TIMEOUT                     = 3000
  protected implicit val timeout: Timeout = Timeout(TIMEOUT, TimeUnit.MILLISECONDS)

}

trait AbstractActorSpec extends AbstractAsyncSpec with BeforeAndAfterAll with ActorSpec {

  override protected def afterAll(): Unit = {
    val terminated = Await.result(actorSystem.terminate(), timeout.duration)
    logger.debug(s"$terminated")
  }

  protected final def interceptAndPrint[T <: Throwable](f: => scala.Any)(implicit manifest: scala.reflect.Manifest[T]): T = {
    val t = intercept[T] {
      f
    }
    logger.error(s"$t")
    t
  }

}

trait SmtpSpec extends ActorSpec {

  import TestUtils._

  protected val host: String = InetAddress.getLocalHost.getHostAddress

  private val defaultMaxSize = 1024

  protected def readTimeout: FiniteDuration = 30 seconds

  protected def maxSize: Int = defaultMaxSize

  protected final val configuration = Configuration(notOccupiedPortNumber, maxSize, readTimeout)

  protected def consumer(mail: Mail): Future[ConsumedResult] = LogConsumer.consumer(mail)

  protected def addressHandler: AddressHandler              = NopAddressHandler
  protected lazy val address: SocketAddress                 = SocketAddress(host, configuration.port)
  protected final lazy val clientStream: StreamClient       = new StreamClient(address)
  protected final lazy val clientWithActor: ClientWithActor = new ClientWithActor(address)(actorSystem, readTimeout)

  protected final val server: StreamServer = StreamServer(consumer, configuration, addressHandler)(actorSystem)
}

trait AbstractSmtpSpec extends AbstractActorSpec with SmtpSpec {

  override protected def beforeAll(): Unit = {}

  override protected def afterAll(): Unit = {
    server.close()
    super.afterAll()

  }

}
