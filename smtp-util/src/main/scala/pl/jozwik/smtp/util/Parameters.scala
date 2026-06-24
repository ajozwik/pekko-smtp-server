package pl.jozwik.smtp
package util

import pl.jozwik.smtp.util.SmtpCodes.*
import pl.jozwik.smtp.util.Response.*

import scala.util.{ Failure, Success, Try }

object Parameters {

  def validate(parameters: Seq[(String, String)], handlers: Map[String, ParameterHandler]): Either[String, Unit] = parameters match {
    case (name, value) +: tail =>
      validateParameterName(handlers, name, value, tail)
    case _ =>
      Right(())
  }

  private def validateParameterName(
      handlers: Map[String, ParameterHandler],
      name: String,
      value: String,
      parameters: Seq[(String, String)]
  ): Either[String, Unit] = {
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
  val DefaultMailSize: Long = 1024L * 1024
}

final case class SizeParameterHandler(size: Long = SizeParameterHandler.DefaultMailSize) extends ParameterHandler {
  val key = "SIZE"

  def validate(t: String): Either[String, Unit] =
    Try(t.toLong) match {
      case Success(s) if s < size =>
        Right(())
      case Failure(th) =>
        Left(s"$SYNTAX_ERROR ${th.getMessage}")
      case Success(_) =>
        Left(sizeExceedsMaximum(size))
    }

}
