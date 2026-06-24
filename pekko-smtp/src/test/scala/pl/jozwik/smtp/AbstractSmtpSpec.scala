package pl.jozwik.smtp

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import pl.jozwik.smtp.client.{ ClientWithActor, StreamClient }
import pl.jozwik.smtp.server.*
import pl.jozwik.smtp.server.consumer.LogConsumer
import pl.jozwik.smtp.util.*

import scala.concurrent.duration.*
import scala.concurrent.{ Await, Future }

object ActorSpec {
  private[smtp] val number = Iterator from 1
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

  import TestUtils.*

  protected val host: String = InetAddress.getLocalHost.getHostAddress

  private val defaultMaxSize = 1024

  protected def readTimeout: FiniteDuration = 30.seconds

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
