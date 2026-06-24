package pl.jozwik.smtp
package server

package object command {

  def response(mailAccumulator: MailAccumulator, line: String): (MailAccumulator, ResponseMessage) =
    (mailAccumulator, TextResponse(line))

  def response(mailAccumulator: MailAccumulator, line: String*): (MailAccumulator, ResponseMessage) =
    (mailAccumulator, MultiLineResponse(line))

  def closeResponse(mailAccumulator: MailAccumulator, line: String): (MailAccumulator, ResponseMessage) =
    (mailAccumulator, QuitResponse(line))

  val EMPTY              = true
  val NOT_EMPTY: Boolean = !EMPTY
  val READ_DATA          = true
}
