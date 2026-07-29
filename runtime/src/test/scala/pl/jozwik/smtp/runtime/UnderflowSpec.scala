package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.WithClient
import pl.jozwik.smtp.server.SpecWithServer

import scala.util.Using
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.{TestUtils, Utils}

import scala.concurrent.duration.DurationInt

class UnderflowSpec extends SpecWithServer with WithClient {

  "Handle test UNDERFLOW" in {
    Using.resource(
      createClient("client")(port)
    ) { implicit client =>
      client.connect()
      TestUtils.waitFor(client.getLastRead.capacity() > 0, 5.millis)
      val ehloResponse = writeAndWaitForRead(s"$EHLO test")
      logger.trace(s"$EHLO ${toMessage(ehloResponse)}")
      val tlsResponse = client.startTls()
      logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
      val half = client.writeSplitAndWaitForRead(Utils.withEndOfLine(s"$EHLO test"))
      logger.trace(s"Half: ${toMessage(half)}")

      val quit = client.writeSplitAndWaitForRead(Utils.withEndOfLine(s"$QUIT"))
      logger.trace(s"Half: ${toMessage(quit)}")
      succeed
    }
  }

}
