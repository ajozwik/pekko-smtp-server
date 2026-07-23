package pl.jozwik.smtp.tls

import java.nio.ByteBuffer
import javax.net.ssl.{SSLEngine, SSLEngineResult}

trait WithSslEngineClient extends WithSslEngine {

  override protected val whoIAm: String       = "client"
  override protected val whoContactMe: String = "server"

  protected override def handleRead(
      engine: Option[SSLEngine]
  )(readByteBuffer: ByteBuffer => Int, closeConn: () => Unit)(implicit seq: Int): (Option[ByteBuffer], Option[SSLEngineResult]) =
    read(engine, _ => (), _ => ())(readByteBuffer, closeConn)

  override protected def ownHandshakeFinished(): Unit  = logger.trace(s"$whoIAm: Handshake finished")
  override protected def peerHandshakeFinished(): Unit = logger.trace(s"$whoIAm: Handshake finished")
}
