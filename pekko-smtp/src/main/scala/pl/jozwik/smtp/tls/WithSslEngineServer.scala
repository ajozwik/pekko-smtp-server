package pl.jozwik.smtp.tls

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.{SSLEngine, SSLEngineResult}

trait WithSslEngineServer extends WithSslEngine {

  override protected val whoIAm: String       = "server"
  override protected val whoContactMe: String = "client"

  protected override def handleRead(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val closed = new AtomicBoolean(false)
    read(_ => (), closed.set)(readByteBuffer, writeByteBuffer, closeConn)

  }

  override protected def ownHandshakeFinished(): Unit  = logger.trace(s"$whoIAm: Handshake finished")
  override protected def peerHandshakeFinished(): Unit = logger.trace(s"$whoIAm: Handshake finished")
}
