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
package pl.jozwik.smtp.util

import java.io.{ BufferedReader, PrintWriter }
import java.net.{ InetAddress, ServerSocket, Socket }
import java.util.Objects
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Constants._

import scala.util.Try

object TestUtils extends StrictLogging {
  def notOccupiedPortNumber: Int = {
    val server = new ServerSocket(0)
    server.setReuseAddress(true)
    val number = server.getLocalPort
    server.close()
    number
  }

  def readAnswer(reader: BufferedReader): String = {
    val line = reader.readLine()
    logger.debug(s"$line")
    if (Objects.isNull(line)) {
      ""
    } else {
      val four = line.take(FOUR)
      if (four.length == FOUR && four.endsWith("-")) {
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

  private val TIMEOUT = 10

  def init(port: Int): Socket = {
    Try {
      new Socket(InetAddress.getLocalHost, port)
    }.getOrElse {
      TimeUnit.MILLISECONDS.sleep(TIMEOUT)
      logger.debug(s"Try again, port number $port")
      init((port + 1) % Character.MAX_VALUE)
    }

  }
}