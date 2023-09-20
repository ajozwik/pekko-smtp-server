package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.{ Configuration, NopAddressHandler, StreamServer }
import pl.jozwik.smtp.server.consumer.{ Consumer, LogConsumer }
import pl.jozwik.smtp.util.{ ConsumedResult, Mail, RuntimeConstants, SizeParameterHandler }

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success, Try }

class Run extends StrictLogging with AutoCloseable {
  private val defaultPort = 1587
  private val port        = Integer.getInteger(RuntimeConstants.portKey, defaultPort)
  private val size        = java.lang.Long.getLong(RuntimeConstants.sizeKey, SizeParameterHandler.DEFAULT_MAIL_SIZE) // max mail size

  private val consumer = {
    val className = System.getProperty(RuntimeConstants.consumerClass, classOf[LogConsumer].getName)
    logger.debug(s"$className")
    Try {
      val clazz = Class.forName(className.replace("$", ""))
      clazz.getConstructors.foreach { c =>
        logger.debug(s"$c")
      }
      clazz.getConstructor().newInstance().asInstanceOf[Consumer]
    }.recover { case th =>
      logger.error("", th)
      throw th
    }
  }

  private val logConsumer: Mail => Future[ConsumedResult] = consumer match {
    case Success(c) =>
      logger.debug(s"$c")
      c.consumer
    case Failure(th) =>
      logger.error(s"$LogConsumer", th)
      LogConsumer.consumer
  }

  private implicit val system: ActorSystem = ActorSystem(s"SMTP$port") // Actor system

  private val configuration = Configuration(port, size, 2.minutes)

  lazy val server: StreamServer = StreamServer(logConsumer, configuration, NopAddressHandler) // NopAddressHandler - accepts all mail addresses

  def close(): Unit =
    server.close()

}
