package pl.jozwik.smtp

import pl.jozwik.smtp.DemoHelper.{TlsVersion, keyStoreClientInputStream, trustStoreInputStream}
import pl.jozwik.smtp.tls.NioSslClient
import pl.jozwik.smtp.util.{ByteBufferHelper, Utils}

import java.nio.ByteBuffer

trait WithClient {

  protected def createClient(name: String)(port: Int) =
    new NioSslClient(TlsVersion, "localhost", port, name, keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
      trustStoreInputStream,
      TlsOpts.trustPassword
    )

  protected def toMessage(b: ByteBuffer): String = ByteBufferHelper.toString(b).trim

  protected def writeAndWaitForRead(txt: String)(implicit client: NioSslClient): ByteBuffer =
    client.writeAndWaitForRead(Utils.withEndOfLine(txt))

}
