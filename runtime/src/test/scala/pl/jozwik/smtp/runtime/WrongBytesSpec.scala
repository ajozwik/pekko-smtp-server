package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.DemoHelper.{TlsVersion, keyStoreClientInputStream, trustStoreInputStream}
import pl.jozwik.smtp.WithNioSslClient
import pl.jozwik.smtp.server.SpecWithServer
import pl.jozwik.smtp.tls.{NioSslClient, TlsOpts}
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.{TestUtils, Utils}

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import scala.concurrent.duration.DurationInt
import scala.util.Using

class WrongBytesSpec extends SpecWithServer with WithNioSslClient {

  "Handle test WrongBytes" in {
    Using.resource(
      new NioSslClient(TlsVersion, "localhost", port, "clientWrong", keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
        trustStoreInputStream,
        TlsOpts.trustPassword
      ) {
        private val it                                                                              = Iterator.from(0)
        protected override def writeToOutputBuffer(b: ByteBuffer)(implicit sc: SocketChannel): Unit = {
          val bb = b.duplicate()
          super.writeToOutputBuffer(b)
          val tt = it.next()
          if (tt > 4) {
            logger.trace(s"Write wrong bytes: $tt")
            super.writeToOutputBuffer(bb)
          }
        }
      }
    ) { implicit client =>
      client.connect()
      TestUtils.waitFor(
        {
          val last = client.getLastRead
          logger.trace(s"Last: ${toMessage(last)} -> ${last.length}")
          last.isEmpty
        },
        5.millis,
        "server"
      )
      val ehloResponse = writeAndWaitForRead(s"$EHLO test")
      logger.trace(s"$EHLO ${toMessage(ehloResponse)}")
      interceptAndPrint[IOException] {
        val tlsResponse = client.startTls()
        logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
        val half = client.writeAndWaitForRead(Utils.withEndOfLine(s"$EHLO test"))
        logger.trace(s"Half: ${toMessage(half)}")
        val quit = client.writeAndWaitForRead(Utils.withEndOfLine(s"$QUIT"))
        logger.trace(s"Half: ${toMessage(quit)}")
      }
      succeed
    }
  }

}
