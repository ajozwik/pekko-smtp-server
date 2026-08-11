package pl.jozwik.smtp.tls

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.{SSLEngine, SSLEngineResult}

trait WithSslEngineServer extends WithSslEngine {

  override protected final val whoContactMe: String = "client"

  protected override def handleRead(
      peerNetData: ByteBuffer
  )(writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val closed = new AtomicBoolean(false)
    read(peerNetData)(_ => (), closed.set)(writeByteBuffer, closeConn)

  }

  override protected def ownHandshakeFinished(remaining: ByteBuffer): Unit = logger.trace(s"$whoIAm: Handshake finished $remaining")
}
