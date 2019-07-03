package pl.jozwik.smtp.server.consumer

import pl.jozwik.smtp.util.{ ConsumedResult, Mail, SuccessfulConsumed }

import scala.concurrent.Future

object LogConsumer extends AbstractConsumer {

  override def consumer(mail: Mail): Future[ConsumedResult] = {
    logger.debug(s"$mail")
    Future.successful(SuccessfulConsumed)
  }
}