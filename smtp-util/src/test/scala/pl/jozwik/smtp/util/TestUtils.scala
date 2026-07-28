package pl.jozwik.smtp.util

import java.io.{BufferedReader, PrintWriter}
import java.net.{InetAddress, ServerSocket, Socket}
import java.util.Objects
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Constants.*

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.util.{Failure, Success, Try}

object TestUtils extends StrictLogging {

  val ReadTimeout: FiniteDuration = 10.seconds

  def notOccupiedPortNumber: Int = {
    val server = new ServerSocket(0)
    server.setReuseAddress(true)
    val number = server.getLocalPort
    server.close()
    logger.trace(s"Port number $number")
    number
  }

  def readAnswerOrError(reader: BufferedReader): Try[String] =
    Try(readAnswer(reader))

  @tailrec
  def readAnswer(reader: BufferedReader): String = {
    val line = reader.readLine()
    logger.trace(s"$line")
    if (Objects.isNull(line)) {
      ""
    } else {
      val four = line.take(Four)
      if (four.length == Four && four.endsWith("-")) {
        readAnswer(reader)
      } else {
        line
      }
    }
  }

  def writeLine(writer: PrintWriter, line: String): Unit = {
    writer.print(Utils.withEndOfLine(line))
    writer.flush()
  }

  private val TIMEOUT = 30

  private val maxRepeat = 20

  @tailrec
  def connect(port: Int, repeat: Int = maxRepeat): Socket =
    Try {
      new Socket(InetAddress.getLocalHost, port)
    } match {
      case Success(s) =>
        s
      case Failure(th) if repeat > 0 =>
        TimeUnit.MILLISECONDS.sleep(TIMEOUT)
        logger.trace(s"Try again, port number $port", th)
        connect(port, repeat - 1)
      case Failure(th) =>
        throw th
    }

  @tailrec
  def waitFor(condition: => Boolean, duration: Duration): Unit =
    if (condition) {
      logger.trace("Waiting for")
      sleep(duration)
      waitFor(condition, duration)
    }

  def sleep(duration: Duration): Unit =
    TimeUnit.MILLISECONDS.sleep(duration.toMillis)

}
