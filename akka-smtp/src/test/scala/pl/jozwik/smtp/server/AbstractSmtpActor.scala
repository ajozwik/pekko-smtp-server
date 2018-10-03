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
package pl.jozwik.smtp.server

import java.net.InetSocketAddress

import akka.actor.PoisonPill
import akka.io.Tcp.{ Bind, Bound, CommandFailed, Unbind }
import akka.io.{ IO, Tcp }
import pl.jozwik.smtp.AbstractActor

abstract class AbstractSmtpActor(bindAddress: InetSocketAddress) extends AbstractActor {
  import context.system

  override def unhandled(message: Any): Unit = message match {
    case b @ Bound(_) =>
      logger.debug(s"$b")
    case CommandFailed(c) =>
      logger.error(s"$c\n${c.failureMessage}")
      self ! PoisonPill
    case _ =>
      super.unhandled(message)
  }

  override def preStart(): Unit = {
    IO(Tcp) ! Bind(self, bindAddress)
    super.preStart()
  }

  override def postStop(): Unit = {
    IO(Tcp) ! Unbind
    super.postStop()
  }
}