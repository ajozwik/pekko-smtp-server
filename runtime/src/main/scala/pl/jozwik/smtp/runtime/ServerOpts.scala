package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.server.consumer.{ Consumer, LogConsumer }
import pl.jozwik.smtp.util.{ ConsumedResult, Mail, RuntimeConstants, SizeParameterHandler }

import scala.concurrent.Future

object ServerOpts {
  private val defaultPort = 1587
  private def size        = java.lang.Long.getLong(RuntimeConstants.sizeKey, SizeParameterHandler.DefaultMailSize).longValue() // max mail size

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def clazz: Consumer = {
    val className = System.getProperty(RuntimeConstants.consumerClass, classOf[LogConsumer].getName)
    val clazz     = Class.forName(className.replace("$", "")).asInstanceOf[Class[Consumer]]
    clazz.getConstructor().newInstance()
  }

  lazy val fromSystemProps: ServerOpts[Consumer] = ServerOpts(Integer.getInteger(RuntimeConstants.portKey, defaultPort).intValue(), size, clazz.consumer)
}

final case class ServerOpts[T <: Consumer](port: Int, size: Long, consumer: Mail => Future[ConsumedResult])
