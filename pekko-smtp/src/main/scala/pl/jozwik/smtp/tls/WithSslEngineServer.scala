package pl.jozwik.smtp.tls

import pl.jozwik.smtp.util.ByteBufferHelper

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.{SSLEngine, SSLEngineResult}

trait WithSslEngineServer extends WithSslEngine {

  override protected val whoIAm: String       = "server"
  override protected val whoContactMe: String = "client"

  protected override def handleRead(readByteBuffer: ByteBuffer => Int, writeByteBuffer: ByteBuffer => Unit, closeConn: () => Unit)(implicit
      seq: Int,
      engine: Option[SSLEngine]
  ): (Option[ByteBuffer], Option[SSLEngineResult]) = {
    val closed = new AtomicBoolean(false)
    read(_ => (), closed.set)(readByteBuffer, writeByteBuffer, closeConn) match {
      case s @ (Some(message), _) =>
        val str = ByteBufferHelper.toString(message)
        if (str.nonEmpty) {
          logger.trace(s"$whoIAm: ($seq) Received message from the $whoContactMe: $str")
        }
        s
      case none =>
        none
    }

  }

  override protected def ownHandshakeFinished(): Unit  = logger.trace(s"$whoIAm: Handshake finished")
  override protected def peerHandshakeFinished(): Unit = logger.trace(s"$whoIAm: Handshake finished")
}
