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
package util

import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.Response._

import scala.util.{Failure, Success, Try}

object Parameters {

  def validate(parameters: Seq[(String, String)], handlers: Map[String, ParameterHandler]): Either[String, Unit] = parameters match {
    case (name, value) +: tail =>
      validateParameterName(handlers, name, value, tail)
    case _ =>
      Right(())
  }

  private def validateParameterName(handlers: Map[String, ParameterHandler], name: String,
    value: String, parameters: Seq[(String, String)]): Either[String, Unit] = {
    def validateValue(handler: ParameterHandler): Either[String, Unit] = {
      handler.validate(value) match {
        case Right(()) =>
          validate(parameters, handlers)
        case l @ Left(error) =>
          l
      }
    }
    handlers.get(name) match {
      case Some(handler) =>
        validateValue(handler)
      case _ =>
        Left(parameterUnrecognized(name))
    }
  }

}

sealed trait ParameterHandler {
  val key: String

  def validate(t: String): Either[String, Unit]
}

object SizeParameterHandler {
  val DEFAULT_MAIL_SIZE = 1024L * 1024
}

case class SizeParameterHandler(size: Long = SizeParameterHandler.DEFAULT_MAIL_SIZE) extends ParameterHandler {
  val key = "SIZE"

  def validate(t: String): Either[String, Unit] =
    Try(t.toLong) match {
      case Success(s) if s < size =>
        Right(())
      case Failure(th) =>
        Left(s"$SYNTAX_ERROR th.getMessage")
      case Success(x) =>
        Left(sizeExceedsMaximum(size))
    }
}