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

import org.apache.pekko.actor.{ Actor, ActorLogging }
import com.typesafe.scalalogging.StrictLogging

trait AbstractActor extends Actor with StrictLogging with ActorLogging {

  val DISCARD = true

  protected def become(state: Receive, stateName: String = ""): Unit = {
    logger.debug(s"$getClass Change state to $stateName")
    context.become(state, DISCARD)
  }

  override def preStart(): Unit = {
    super.preStart()
    logger.debug(s"$getClass $self preStart")
  }

  override def postStop(): Unit = {
    super.postStop()
    logger.debug(s"$self postStop")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    logger.debug(s"$self preRestart $message  $hashCode", reason)
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    logger.debug(s"$self postRestart ${reason.getMessage}")
    super.postRestart(reason)
  }

  override def unhandled(message: Any): Unit = {
    logger.error(s"$getClass Unhandled message in `$self` message `$message` from ${sender()}")
    super.unhandled(message)
  }
}