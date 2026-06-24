package pl.jozwik.smtp.util

import java.io.{ BufferedReader, PrintWriter }
import java.net.{ InetAddress, ServerSocket, Socket }
import java.util.Objects
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Constants.*

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

object TestUtils extends StrictLogging {

  def notOccupiedPortNumber: Int = {
    val server = new ServerSocket(0)
    server.setReuseAddress(true)
    val number = server.getLocalPort
    server.close()
    number
  }

  def readAnswerOrError(reader: BufferedReader): Try[String] =
    Try(readAnswer(reader))

  @tailrec
  def readAnswer(reader: BufferedReader): String = {
    val line = reader.readLine()
    logger.debug(s"$line")
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
  def init(port: Int, repeat: Int = maxRepeat): Socket =
    Try {
      new Socket(InetAddress.getLocalHost, port)
    } match {
      case Success(s) =>
        s
      case Failure(th) if repeat > 0 =>
        TimeUnit.MILLISECONDS.sleep(TIMEOUT)
        logger.debug(s"Try again, port number $port", th)
        init(port, repeat - 1)
      case Failure(th) =>
        throw th
    }

}
