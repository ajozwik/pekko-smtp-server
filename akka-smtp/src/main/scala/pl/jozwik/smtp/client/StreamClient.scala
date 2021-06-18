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
package pl.jozwik.smtp.client

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, Framing, Source, Tcp }
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.AkkaUtils
import pl.jozwik.smtp.util.{ Constants, Mail, SocketAddress, Utils }

import scala.concurrent.Future

class StreamClient(host: String, port: Int)(implicit system: ActorSystem) extends StrictLogging {

  def this(serverAddress: SocketAddress)(implicit system: ActorSystem) =
    this(serverAddress.host, serverAddress.port)

  private val connection: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
    Tcp().outgoingConnection(host, port)

  def sendMail(mail: Mail): Future[Result] = {
    import Constants._
    val future = Source
      .single(mail)
      .map { mail =>
        Seq(s"$EHLO client", s"$MAIL_FROM: ${mail.from}") ++
          mail.to.map(to => s"$RCPT_TO:$to") ++
          Seq(s"$DATA", s"$Subject:${mail.emailContent.subject}", "", mail.emailContent.txtBody.getOrElse(""), END_DATA, QUIT)
      }
      .map(seq => ByteString(seq.map(Utils.withEndOfLine).mkString))
      .via(connection)
      .via(Framing.delimiter(ByteString("\n"), Constants.maximumFrameLength, allowTruncation = true))
      .runFold[(Result, Seq[Int])]((SuccessResult, Seq.empty[Int])) { case ((acc, codes), message) =>
        val response = AkkaUtils.toInt(message.take(3).utf8String)
        logger.debug(s"${message.utf8String}")
        val newAcc = acc match {
          case f: FailedResult =>
            f
          case _ if isResponseSuccess(response) =>
            acc
          case _ =>
            FailedResult((message.utf8String + Constants.delimiter).stripLineEnd)
        }

        (newAcc, response.map(c => c +: codes).getOrElse(codes))
      }
    mapToResult(future)
  }

  private def mapToResult(future: Future[(Result, Seq[Int])]) = {
    import system.dispatcher
    future
      .map {
        case (SuccessResult, codes) if !codes.containsSlice(Seq(Constants.REQUEST_COMPLETE, Constants.START_MAIL_INPUT)) =>
          FailedResult("")
        case (result, _) =>
          result

      }
      .recover { case e =>
        logger.error("", e)
        FailedResult(e.getMessage)
      }
  }

  private def isResponseSuccess(response: Option[Int]) = {
    response.exists(r => r >= 200 && r < 400)
  }
}
