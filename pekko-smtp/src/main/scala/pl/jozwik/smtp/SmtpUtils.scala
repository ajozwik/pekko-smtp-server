package pl.jozwik.smtp

import org.apache.pekko.io.Tcp.Write
import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Utils

import scala.util.Try

object SmtpUtils extends StrictLogging {
  import Utils.withEndOfLine
  def toWrite(line: String): Write = Write(ByteString(withEndOfLine(line)))

  def toInt(code: String): Option[Int] = Try(code.toInt).toOption
}
