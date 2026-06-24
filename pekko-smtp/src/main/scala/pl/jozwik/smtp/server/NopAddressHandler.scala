package pl.jozwik.smtp
package server

import pl.jozwik.smtp.util.*

object NopAddressHandler extends AddressHandler {

  def acceptFrom(from: MailAddress): Boolean = true

  def acceptTo(from: MailAddress): Boolean = true
}
