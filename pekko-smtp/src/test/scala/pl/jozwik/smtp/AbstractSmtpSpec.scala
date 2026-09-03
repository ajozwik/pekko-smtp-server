package pl.jozwik.smtp

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.stream.scaladsl.Tcp
import org.scalatest.BeforeAndAfterAll
import pl.jozwik.smtp.client.{ClientWithActor, StreamClient}
import pl.jozwik.smtp.server.*
import pl.jozwik.smtp.server.consumer.LogConsumer
import pl.jozwik.smtp.util.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

object ActorSpec {
  private[smtp] val number = Iterator from 1
}

trait ActorSpec extends WithTagged with StrictLogging {

  protected implicit val actorSystem: ActorSystem =
    ActorSystem(s"${getClass.getSimpleName}-${ActorSpec.number.next()}", ConfigFactory.parseResources("application-test.conf"))

  private val TIMEOUT                     = 3000
  protected implicit val timeout: Timeout = Timeout(TIMEOUT, TimeUnit.MILLISECONDS)

}

trait AbstractWithActorSystemSpec extends AbstractAsyncSpec with BeforeAndAfterAll with ActorSpec {

  override protected def afterAll(): Unit = {
    val terminated = Await.result(actorSystem.terminate(), timeout.duration)
    logger.trace(s"$terminated")
  }

  protected final def interceptAndPrint[T <: Throwable: ClassTag](f: => scala.Any): T = {
    val t = intercept[T] {
      f
    }
    logger.error(s"$t", t)
    t
  }

}

trait SmtpSpec extends ActorSpec with WithPort {

  protected val host: String = InetAddress.getLocalHost.getHostAddress

  private val defaultMaxSize = 1024

  protected implicit def readTimeout: FiniteDuration = 30.seconds

  protected def maxSize: Int = defaultMaxSize

  protected def consumer(mail: Mail): Future[ConsumedResult] = LogConsumer.consumer(mail)
  protected def createClientActor(address: SocketAddress)    = new ClientWithActor(address)
  protected def addressHandler: AddressHandler               = NopAddressHandler
  protected lazy val address: SocketAddress                  = SocketAddress(host, port)
  protected final lazy val clientStream: StreamClient        = new StreamClient(address, tagged("client"))
  protected final lazy val clientWithActor: ClientWithActor  = createClientActor(address)
  private def connectionHandler(whoIAm: String) = ConnectionHandler.connectionHandler(maxSize, consumer, readTimeout, tagged(whoIAm), addressHandler)()
  protected final val server: StreamServer      = StreamServer((host, port) => Tcp().bind(host, port), port, tagged("server"))(connectionHandler)
}

trait AbstractSmtpSpec extends AbstractWithActorSystemSpec with SmtpSpec {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    TestUtils.waitFor(!server.isBound, 10.millis, "server")
  }

  override protected def afterAll(): Unit = {
    server.close()
    super.afterAll()

  }

}
