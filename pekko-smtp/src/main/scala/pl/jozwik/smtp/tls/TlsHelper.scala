package pl.jozwik.smtp.tls

import javax.net.ssl.SSLEngineResult.{HandshakeStatus, Status}
import javax.net.ssl.{SSLEngine, SSLEngineResult}
import scala.annotation.tailrec

object TlsHelper {

  def failedHandshakeResult(status: HandshakeStatus): SSLEngineResult = new SSLEngineResult(Status.CLOSED, status, 0, 0)

  @tailrec
  def runDelegatedTasks(engine: SSLEngine): Unit =
    Option(engine.getDelegatedTask) match {
      case Some(task) =>
        task.run()
        runDelegatedTasks(engine)
      case _ =>
    }

  def toApplicationBufferSize(engine: Option[SSLEngine], applicationBufferSize: Int): Int =
    engine.map(_.getSession.getApplicationBufferSize).getOrElse(applicationBufferSize)

  def toPacketBufferSize(engine: Option[SSLEngine], packetBufferSize: Int): Int =
    engine.map(_.getSession.getPacketBufferSize).getOrElse(packetBufferSize)

}
