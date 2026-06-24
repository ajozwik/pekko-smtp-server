package pl.jozwik.smtp
package server

import pl.jozwik.smtp.util.SmtpCodes.REQUEST_COMPLETE

import java.util.concurrent.TimeUnit
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.TestUtils.*

import scala.concurrent.duration.*
import scala.util.{ Failure, Success }

class SmtpServerTimeoutSpec extends AbstractSmtpSpec with SocketSpec {

  lazy val port: Int = configuration.port

  override protected def readTimeout: FiniteDuration = (timeLimit / 2).min(1.second)

  override protected def afterAll(): Unit = {
    close()
    super.afterAll()
  }

  "SmtpServer " should {

    s"Handle $DATA ERROR" in {
      readAnswer(reader)
      writeLine(writer, s"$HELO")
      val probablyTimeout = readAnswer(reader)
      TimeUnit.MILLISECONDS.sleep(readTimeout.toMillis)
      readAnswerOrError(reader) match {
        case Success(v) =>
          fail(v)
        case Failure(th) =>
          logger.error("", th)
      }
      probablyTimeout should startWith(s"$REQUEST_COMPLETE")
    }

  }

}
