package pl.jozwik.smtp.tls

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus

object Attachment {
  def empty: Attachment = Attachment(None)
}

final case class Attachment(
    engine: Option[SSLEngine],
    buffers: Buffers = Buffers.empty,
    handshakeStatus: AtomicReference[HandshakeStatus] = new AtomicReference(HandshakeStatus.NOT_HANDSHAKING),
    open: AtomicBoolean = new AtomicBoolean(true)
) {

  def withEngine(engine: SSLEngine, buffers: Buffers): Attachment = {
    val status = engine.getHandshakeStatus
    handshakeStatus.set(status)
    copy(engine = Option(engine), buffers = buffers, handshakeStatus = handshakeStatus)
  }

  def clearBuffers: Attachment = copy(buffers = buffers.clear)

}
