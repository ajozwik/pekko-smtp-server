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
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing, Source, Tcp}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.{Constants, Mail, Utils}

import scala.concurrent.Future

class StreamClient(address: String, port: Int)(implicit system: ActorSystem, m: Materializer) extends StrictLogging {

  private val connection: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
    Tcp().outgoingConnection(address, port)

  def sendMail(mail: Mail): Future[Result] = {
    import Constants._
    Source.single(mail).map { mail =>
      Seq(
        EHLO,
        s"$MAIL_FROM: ${mail.from}"
      ) ++
        mail.to.map(to => s"$RCPT_TO:$to") ++
        Seq(
          s"$DATA",
          s"$Subject:${mail.emailContent.subject}",
          "",
          mail.emailContent.txtBody.getOrElse(""),
          END_DATA,
          QUIT
        )
    }.map(seq => ByteString(seq.map(Utils.withEndOfLine).mkString))
      .via(connection).via(Framing.delimiter(
        ByteString("\n"),
        Constants.maximumFrameLength,
        allowTruncation = true
      )).runFold(SuccessResult) {
        case (acc, message) =>
          logger.debug(s"${message.utf8String}")
          acc
      }
  }
}