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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Props
import pl.jozwik.smtp.AbstractActor

object ClientActor {
  def props(): Props = Props[ClientActor]
}

class ClientActor extends AbstractActor {
  private val counter = new AtomicInteger()

  def handleMessage(success: Int, failed: Int): Receive = {
    case MailWithAddress(mail, address) =>
      context.actorOf(ClientActorHandler.props(sender(), address, mail), "ClientActorHandler-" + counter.getAndIncrement())
      ()

    case Counter(senderRef, result) =>
      logger.debug(s"END: $result")
      senderRef ! result
      result match {
        case SuccessResult =>
          become(handleMessage(success + 1, failed), "handleMessage")
        case FailedResult(_) =>
          become(handleMessage(success, failed + 1), "handleMessage")
      }
  }

  def receive: Receive = handleMessage(1, 1)
}