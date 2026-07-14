package pl.jozwik.smtp

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.DemoHelper.{ TlsVersion, keyStoreClientInputStream, trustStoreInputStream }
import pl.jozwik.smtp.tls.NioSslClient
import pl.jozwik.smtp.util.{ ByteBufferHelper, Utils }

import java.nio.ByteBuffer
import scala.util.Using

trait Demo extends StrictLogging {

  private def toMessage(b: ByteBuffer): String = ByteBufferHelper.toString(b).trim

  def action(name: String)(port: Int): Unit =
    Using.resource(
      new NioSslClient(TlsVersion, "localhost", port, name, keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
        trustStoreInputStream,
        TlsOpts.trustPassword
      )
    ) { client =>
      client.connect()
      client.writeAndWaitForRead(Utils.withEndOfLine(s"EHLO $name!"))
      val tlsResponse = client.startTls()
      logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
      val bb = client.writeAndWaitForRead(Utils.withEndOfLine(s"EHLO again_$name!"))
      logger.trace(s"${toMessage(bb)}")
      val quitResponse = client.writeAndWaitForRead(Utils.withEndOfLine(s"QUIT"))
      logger.trace(s"${toMessage(quitResponse)}")
    }

}
