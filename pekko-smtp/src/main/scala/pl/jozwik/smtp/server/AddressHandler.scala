package pl.jozwik.smtp.server

import pl.jozwik.smtp.util.MailAddress

trait AddressHandler {

  def acceptFrom(from: MailAddress): Boolean

  def acceptTo(from: MailAddress): Boolean

}
