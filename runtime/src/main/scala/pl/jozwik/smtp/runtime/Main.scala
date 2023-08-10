package pl.jozwik.smtp.runtime

import org.apache.pekko.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.server.{ Configuration, NopAddressHandler, StreamServer }
import pl.jozwik.smtp.server.consumer.{ Consumer, LogConsumer }
import pl.jozwik.smtp.util.{ ConsumedResult, Mail, RuntimeConstants, SizeParameterHandler }

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success, Try }

object Main extends App with StrictLogging {

  private val defaultPort = 1587

  private val port = Integer.getInteger(RuntimeConstants.portKey, defaultPort)

  private val size = java.lang.Long.getLong(RuntimeConstants.sizeKey, SizeParameterHandler.DEFAULT_MAIL_SIZE) // max mail size

  private val consumer = Try {
    val className = System.getProperty(RuntimeConstants.consumerClass, classOf[LogConsumer].getName)
    logger.debug(s"$className")
    Class.forName(className).getConstructor().newInstance().asInstanceOf[Consumer]
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

  val server = StreamServer(logConsumer, configuration, NopAddressHandler) // NopAddressHandler - accepts all mail addresses

}
