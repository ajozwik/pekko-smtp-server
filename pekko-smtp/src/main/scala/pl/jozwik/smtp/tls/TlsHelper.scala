package pl.jozwik.smtp.tls

import javax.net.ssl.SSLEngineResult.{HandshakeStatus, Status}
import javax.net.ssl.{SSLEngine, SSLEngineResult}
import scala.annotation.tailrec

object TlsHelper {

  def failedHandshakeResult(status: HandshakeStatus): SSLEngineResult = new SSLEngineResult(Status.CLOSED, status, 0, 0)

  @tailrec
  def runDelegatedTasks(implicit engine: SSLEngine): Unit =
    Option(engine.getDelegatedTask) match {
      case Some(task) =>
        task.run()
        runDelegatedTasks
      case _ =>
    }

  def toApplicationBufferSize(defaultApplicationBufferSize: Int)(implicit engine: Option[SSLEngine]): Int =
    engine.map(_.getSession.getApplicationBufferSize).getOrElse(defaultApplicationBufferSize)

  def toPacketBufferSize(defaultPacketBufferSize: Int)(implicit engine: Option[SSLEngine]): Int =
    engine.map(_.getSession.getPacketBufferSize).getOrElse(defaultPacketBufferSize)

}
