package pl.jozwik.smtp.server

import org.apache.pekko.stream.scaladsl.Tcp
import pl.jozwik.smtp.server.consumer.LogConsumer
import pl.jozwik.smtp.tls.EphemeralTls
import pl.jozwik.smtp.util.TestUtils
import pl.jozwik.smtp.{AbstractWithActorSystemSpec, WithPort}

import scala.concurrent.duration.DurationInt

trait SpecWithServer extends AbstractWithActorSystemSpec with WithPort {
  private val serverTlsOpts = EphemeralTls.serverTlsOpts

  private val run =
    createServer

  protected def createServer = new StreamServerRunner((host, port) => Tcp().bind(host, port))(
    ServerOpts(port, 10 * 1000, LogConsumer.consumer, readTimeout = TestUtils.ReadTimeout),
    Option(serverTlsOpts)
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    TestUtils.waitFor(!run.isBound, 10.millis)
  }

  override protected def afterAll(): Unit = {
    run.close()
    super.afterAll()
  }

}
