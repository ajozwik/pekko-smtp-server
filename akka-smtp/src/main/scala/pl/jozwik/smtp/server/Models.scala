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

import java.time.ZonedDateTime

import akka.actor.Props
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.Utils._
import pl.jozwik.smtp.util.{ MailAddress, SizeParameterHandler }

import scala.concurrent.duration._

case class PropsWithName(props: Props, name: String)

case class Configuration(port: Int, size: Long = SizeParameterHandler.DEFAULT_MAIL_SIZE, readTimeout: FiniteDuration = 1 hour)

private[server] case object TickTimeout

case class Content(content: IndexedSeq[String] = IndexedSeq.empty[String], size: Int = 0)

sealed trait ResponseMessage

case class MultiLineResponse(message: Seq[String]) extends ResponseMessage

case class TextResponse(message: String) extends ResponseMessage

case object NoResponse extends ResponseMessage

case object NoDataResponse extends ResponseMessage

case class QuitResponse(message: String) extends ResponseMessage

object MailAccumulator {
  val empty = MailAccumulator(NEED_HELLO)
  val withHello = MailAccumulator(!NEED_HELLO)
}

case class MailAccumulator(
    needHello: Boolean = false,
    from: MailAddress = MailAddress.empty,
    to: Seq[MailAddress] = Seq.empty[MailAddress],
    content: Content = Content(),
    readData: Boolean = false,
    notCompletedLine: Option[String] = None,
    lastMessageTimestamp: ZonedDateTime = now) {
  def addLine(line: String): MailAccumulator = {
    this.copy(content = Content(content.content :+ line, content.size + line.length))
  }
}