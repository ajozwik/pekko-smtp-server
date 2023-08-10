package pl.jozwik.smtp.server.consumer

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.{ ConsumedResult, Mail }

import scala.concurrent.Future

trait AbstractConsumer extends Consumer with StrictLogging

trait Consumer {
  def consumer(mail: Mail): Future[ConsumedResult]
}
