package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.WithNioSslClient
import pl.jozwik.smtp.server.SpecWithServer

import scala.util.Using
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.{TestUtils, Utils}

import scala.concurrent.duration.DurationInt

class UnderflowSpec extends SpecWithServer with WithNioSslClient {

  "Handle test UNDERFLOW" in {
    Using.resource(
      createClient("clientUnderflow")(port)
    ) { implicit client =>
      client.connect()
      TestUtils.waitFor(
        {
          val last = client.getLastRead
          logger.trace(s"Last: ${toMessage(last)} -> ${last.length}")
          last.isEmpty
        },
        5.millis,
        "serverUnderflow"
      )
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
