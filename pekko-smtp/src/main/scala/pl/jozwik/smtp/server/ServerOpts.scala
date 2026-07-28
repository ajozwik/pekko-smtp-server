package pl.jozwik.smtp.server

import pl.jozwik.smtp.server.consumer.{Consumer, LogConsumer}
import pl.jozwik.smtp.util.{ConsumedResult, Mail, RuntimeConstants, SizeParameterHandler}

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ServerOpts {
  private val defaultPort = 1587
  private def size        = RuntimeConstants.sizeKey.valueOrDefault(SizeParameterHandler.DefaultMailSize).toLong // max mail size
  private def port        = RuntimeConstants.portKey.valueOrDefault(defaultPort).toInt

  private def clazz: Consumer = {
    val className = RuntimeConstants.consumerClass.valueOrDefault(classOf[LogConsumer].getName)
    Class.forName(className.replace("$", "")).getConstructor().newInstance() match {
      case c: Consumer => c
      case _           => throw new IllegalArgumentException(s"Class $className is not a Consumer")
    }
  }

  lazy val fromSystemProps: ServerOpts[Consumer] =
    ServerOpts(port, size, clazz.consumer)

}

final case class ServerOpts[T <: Consumer](
    port: Int,
    maxSize: Long,
    consumer: Mail => Future[ConsumedResult],
    readTimeout: FiniteDuration = 2.minutes
)
