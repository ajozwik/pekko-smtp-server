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
package command

import java.net.InetSocketAddress

import pl.jozwik.smtp.util.Constants._

object HelloCommand {

  def handleEhlo(localHostName: String, remote: InetSocketAddress, size: Long): (MailAccumulator, ResponseMessage) = {
    val welcomeLine =
      s"$REQUEST_COMPLETE-$localHostName Hello ${remote.getHostName} " +
        s"[${remote.getAddress.getHostAddress}] pleased to meet you."

    response(MailAccumulator.withHello, welcomeLine, OK_8_BIT, s"$OK_SIZE $size", OK_PIPELINE)

  }

  def handleHelo(localHostName: String, remote: InetSocketAddress): (MailAccumulator, ResponseMessage) = {
    val welcomeLine =
      s"$REQUEST_COMPLETE $localHostName Hello ${remote.getHostName} " +
        s"[${remote.getAddress.getHostAddress}] pleased to meet you."
    response(MailAccumulator.withHello, welcomeLine)

  }
}