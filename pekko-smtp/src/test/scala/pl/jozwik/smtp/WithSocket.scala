package pl.jozwik.smtp

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.TestUtils

import java.io.{BufferedReader, InputStreamReader, PrintWriter}

import java.net.Socket

trait WithPort extends StrictLogging {

  protected lazy val port: Int = TestUtils.notOccupiedPortNumber()

}

trait WithSocket extends AutoCloseable with WithPort {

  protected lazy val socket: Socket         = TestUtils.connect(port)
  protected lazy val writer: PrintWriter    = new PrintWriter(socket.getOutputStream)
  protected lazy val reader: BufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream))

  override def close(): Unit = socket.close()

}
