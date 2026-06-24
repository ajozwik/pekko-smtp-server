package pl.jozwik.smtp

import java.io.{ BufferedReader, InputStreamReader, PrintWriter }
import pl.jozwik.smtp.util.TestUtils.*

import java.net.Socket

trait SocketSpec extends AutoCloseable {

  def port: Int
  protected lazy val socket: Socket         = init(port)
  protected lazy val writer: PrintWriter    = new PrintWriter(socket.getOutputStream)
  protected lazy val reader: BufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream))

  override def close(): Unit = socket.close()

}
