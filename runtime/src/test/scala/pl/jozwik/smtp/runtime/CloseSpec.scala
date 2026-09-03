package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.DemoHelper.{TlsVersion, keyStoreClientInputStream, trustStoreInputStream}
import pl.jozwik.smtp.WithNioSslClient
import pl.jozwik.smtp.server.SpecWithServer
import pl.jozwik.smtp.tls.{NioSslClient, TlsOpts}
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.{TestUtils, Utils}

import java.io.IOException
import scala.concurrent.duration.DurationInt
import scala.util.Using

class CloseSpec extends SpecWithServer with WithNioSslClient {

  "Handle test WrongBytes" in {
    Using.resource(
      new NioSslClient(TlsVersion, "localhost", port, "clientWrong", keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
        trustStoreInputStream,
        TlsOpts.trustPassword
      )
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
        client.close()
        val tlsResponse = client.startTls()
        logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
      }
      succeed
    }
  }

}
