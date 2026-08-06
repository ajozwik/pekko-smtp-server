package pl.jozwik.smtp

import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.DemoHelper.{TlsVersion, keyStoreClientInputStream, trustStoreInputStream}
import pl.jozwik.smtp.tls.{NioSslClient, TlsOpts}
import pl.jozwik.smtp.util.Utils

import java.nio.ByteBuffer

trait WithNioSslClient {

  protected def createClient(name: String)(port: Int) =
    new NioSslClient(TlsVersion, "localhost", port, name, keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
      trustStoreInputStream,
      TlsOpts.trustPassword
    )

  protected def toMessage(b: ByteString): String = b.utf8String.trim

  protected def toMessage(b: ByteBuffer): String = toMessage(ByteString(b))

  protected def writeAndWaitForRead(txt: String)(implicit client: NioSslClient): ByteString =
    client.writeAndWaitForRead(Utils.withEndOfLine(txt))

}
