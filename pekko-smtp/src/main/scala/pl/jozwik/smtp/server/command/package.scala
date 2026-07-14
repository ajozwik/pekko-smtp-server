package pl.jozwik.smtp
package server

package object command {

  val EMPTY              = true
  val NOT_EMPTY: Boolean = !EMPTY
  val READ_DATA          = true

  def response(line: String)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    (acc, TextResponse(line))

  def response(line: String*)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    (acc, MultiLineResponse(line))

  def closeResponse(line: String)(implicit acc: MailAccumulator): (MailAccumulator, ResponseMessage) =
    (acc, QuitResponse(line))

}
