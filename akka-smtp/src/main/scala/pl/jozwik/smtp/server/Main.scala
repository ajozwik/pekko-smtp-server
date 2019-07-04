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
package server

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.consumer.{ Consumer, LogConsumer }
import pl.jozwik.smtp.util._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object NopAddressHandler extends AddressHandler {

  def acceptFrom(from: MailAddress): Boolean = true

  def acceptTo(from: MailAddress): Boolean = true
}

object Main extends App with StrictLogging {

  private val defaultPort = 1587

  private val port = Integer.getInteger(RuntimeConstants.portKey, defaultPort)

  private val size = java.lang.Long.getLong(RuntimeConstants.sizeKey, SizeParameterHandler.DEFAULT_MAIL_SIZE) // max mail size

  private val consumer = Try {
    val className = System.getProperty(RuntimeConstants.consumerClass, LogConsumer.getClass.getName)
    Class.forName(className).getConstructor().newInstance().asInstanceOf[Consumer]
  }

  private val logConsumer: Mail => Future[ConsumedResult] = consumer match {
    case Success(c) =>
      logger.debug(s"$c")
      c.consumer
    case Failure(th) =>
      logger.error(s"$LogConsumer", th)
      LogConsumer.consumer
  }

  private implicit val system: ActorSystem = ActorSystem(s"SMTP$port") // Actor system

  private implicit val m: Materializer = ActorMaterializer()

  private val configuration = Configuration(port, size, 2.minutes)

  val server = StreamServer(logConsumer, configuration, NopAddressHandler) // NopAddressHandler - accepts all mail addresses

}