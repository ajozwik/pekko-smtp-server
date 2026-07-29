package pl.jozwik.smtp.tls

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.{SSLEngine, SSLEngineResult}

trait WithSslEngineClient extends WithSslEngine {

  override protected val whoIAm: String       = "client"
  override protected val whoContactMe: String = "server"

  protected override def handleRead(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine],
      underflowBuffer: AtomicReference[ByteBuffer]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) =
    read(_ => (), _ => ())(readByteBuffer, writeByteBuffer, closeConn)

  override protected def ownHandshakeFinished(): Unit  = logger.trace(s"$whoIAm: Handshake finished")
  override protected def peerHandshakeFinished(): Unit = logger.trace(s"$whoIAm: Handshake finished")
}
