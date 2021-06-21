package pl.jozwik.smtp.client

import scala.concurrent.Future
import pl.jozwik.smtp.util.Mail

trait SenderClient {
  def sendMail(mail: Mail): Future[Result]
}
