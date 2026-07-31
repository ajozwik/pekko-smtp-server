package pl.jozwik.smtp.runtime

import org.apache.pekko.stream.scaladsl.Tcp
import org.scalatest.{Assertion, BeforeAndAfter, BeforeAndAfterAll}
import pl.jozwik.smtp.server.ServerOpts
import pl.jozwik.smtp.server.StreamServerRunner
import pl.jozwik.smtp.tls.TlsOpts
import pl.jozwik.smtp.util.Constants.QUIT
import pl.jozwik.smtp.util.SmtpCodes.CLOSING_TERMINATION_CHANNEL
import pl.jozwik.smtp.{ActorSpec, WithSocket}
import pl.jozwik.smtp.util.{AbstractAsyncSpec, ConsumedResult, Mail, TestUtils}
import pl.jozwik.smtp.util.TestUtils.*

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

abstract class SmtpServerSpec(consumer: Mail => Future[ConsumedResult], tlsOpts: Option[TlsOpts])
  extends AbstractAsyncSpec
  with BeforeAndAfter
  with BeforeAndAfterAll
  with WithSocket
  with ActorSpec {

  logger.trace(s"PORT=$port $consumer")
  protected val sizeOfMailBody: Int = 10 * 1000

  private lazy val r =
    new StreamServerRunner((host, port) => Tcp().bind(host, port))(ServerOpts(port, sizeOfMailBody, consumer, readTimeout = TestUtils.ReadTimeout), tlsOpts)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    TestUtils.waitFor(!r.isBound, 10.millis)
    logger.trace(s"${readAnswer(reader)}")
  }

  override protected def afterAll(): Unit = {
    writeLineAndValidateAnswer(s"$QUIT", CLOSING_TERMINATION_CHANNEL)
    r.close()
    close()
    Await.result(actorSystem.terminate(), TimeoutSeconds.seconds)
    super.afterAll()
  }

  protected def writeLineAndValidateAnswer(line: String, returnCode: Int): Assertion = {
    writeLine(writer, line)
    readAnswer(reader) should startWith(s"$returnCode")
  }

}
