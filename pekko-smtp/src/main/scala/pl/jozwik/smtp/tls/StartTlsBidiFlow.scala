package pl.jozwik.smtp.tls

import com.typesafe.scalalogging.StrictLogging
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.TLSProtocol.{ SendBytes, SessionBytes, SslTlsOutbound }
import org.apache.pekko.stream.{ BidiShape, scaladsl }
import org.apache.pekko.util.ByteString

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import javax.net.ssl.{ SSLContext, SSLEngine, SSLSession }

object StartTlsBidiFlow extends StrictLogging {
  private val dummySession: SSLSession = SSLContext.getDefault.createSSLEngine.getSession

  def apply(tls: AtomicBoolean, createSSLEngine: () => SSLEngine): scaladsl.BidiFlow[SslTlsOutbound, ByteString, ByteString, SessionBytes, NotUsed] =
    scaladsl.BidiFlow.fromGraph(scaladsl.GraphDSL.create() { implicit b =>
      val sslEngine = new AtomicReference[SSLEngine]()

      val top = b.add(scaladsl.Flow[SslTlsOutbound].collect { case SendBytes(bytes) =>
        logger.debug(s"Received: ${bytes.utf8String}")
        if (tls.get) {
          SSLContextFactory.initEngine(sslEngine)(createSSLEngine) match {
            case (_, true) =>
              bytes
            case (e, false) =>
              val buffer = ByteBuffer.allocate(bytes.length * 3)
              val status = try {
                e.wrap(bytes.asByteBuffer, buffer)
              } catch {
                case th: Exception =>
                  logger.error("", th)
                  throw th
              }
              logger.debug(s"${e.getSession} status: $status")
              ByteString(buffer)
          }

        } else {
          bytes
        }
      })
      val bottom = b.add(scaladsl.Flow[ByteString].map { bytes =>
        if (tls.get) {
          val (e, _) = SSLContextFactory.initEngine(sslEngine)(createSSLEngine)
          val buffer = ByteBuffer.allocate(bytes.length * 3)
          val status = try {
            e.unwrap(bytes.asByteBuffer, buffer)
          } catch {
            case th: Throwable =>
              logger.error("", th)
              throw th
          }
          logger.debug(s"${e.getSession} status: $status")
          val bs = ByteString(buffer)
          logger.debug(s"Received: ${bs.utf8String}")
          SessionBytes(e.getSession, bs)
        } else {
          SessionBytes(dummySession, bytes)
        }
      })
      BidiShape.fromFlows(top, bottom)
    })

}
