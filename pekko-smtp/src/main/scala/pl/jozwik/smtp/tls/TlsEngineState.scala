package pl.jozwik.smtp.tls

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus

object TlsEngineState {
  def empty: TlsEngineState = TlsEngineState(None)
}

final case class TlsEngineState(
    engine: Option[SSLEngine],
    buffers: Buffers = Buffers.empty,
    handshakeStatus: AtomicReference[HandshakeStatus] = new AtomicReference(HandshakeStatus.NOT_HANDSHAKING),
    open: AtomicBoolean = new AtomicBoolean(true)
) {

  def withEngine(buffers: Buffers)(implicit engine: SSLEngine): TlsEngineState = {
    val status = engine.getHandshakeStatus
    handshakeStatus.set(status)
    copy(engine = Option(engine), buffers = buffers, handshakeStatus = handshakeStatus)
  }

  def clearBuffers: TlsEngineState = copy(buffers = buffers.clear)

}
