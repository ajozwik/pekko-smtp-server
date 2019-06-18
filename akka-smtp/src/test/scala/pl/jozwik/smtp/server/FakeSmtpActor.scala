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

import java.net.InetSocketAddress

import akka.actor.Props
import akka.io.Tcp._
import pl.jozwik.smtp.AkkaUtils._
import pl.jozwik.smtp.util.Constants._

object FakeSmtpActor {
  def props(bindAddress: InetSocketAddress): Props = Props(new FakeSmtpActor(bindAddress))
}

class FakeSmtpActor(bindAddress: InetSocketAddress) extends AbstractSmtpActor(bindAddress) {
  def receive: Receive = {

    case Connected(_, _) =>
      sender() ! Register(self)
      sender() ! toWrite(s"$SERVICE_READY SMTP DEMO")

    case Received(data) =>
      val str = data.utf8String
      logger.debug(s"$str")
      sender() ! toWrite(s"ALA MA KOTA")
  }
}