package pl.jozwik.smtp.tls

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.{SSLEngine, SSLEngineResult}

trait WithSslEngineClient extends WithSslEngineClientBase {
  override protected val whoIAm: String       = "client"
  override protected val whoContactMe: String = "server"

  override protected def ownHandshakeFinished(remaining: ByteBuffer): Unit = logger.trace(s"$whoIAm: Own Handshake finished $remaining")
}

trait WithSslEngineClientBase extends WithSslEngine {

  protected override def handleRead(
      peerNetData: ByteBuffer
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): (Option[ByteBuffer], Option[SSLEngineResult]) =
    read(peerNetData)(_ => (), _ => ())(writeByteBuffer, closeConn)

}
