package pl.jozwik.smtp.tls

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.TLSProtocol.{SendBytes, SessionBytes, SslTlsOutbound}
import org.apache.pekko.stream.scaladsl.{GraphDSL, Source}
import org.apache.pekko.stream.{BidiShape, FlowShape, Graph, scaladsl}
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.util.{Constants, SmtpResponses, Utils}

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.{SSLContext, SSLEngine, SSLSession}

object StartTlsBidiFlow extends WithSslEngineServer {

  private val dummySession: SSLSession                                = SSLContext.getDefault.createSSLEngine.getSession
  protected val (applicationBufferSize, packetBufferSize)             = (dummySession.getApplicationBufferSize, dummySession.getPacketBufferSize)
  protected override def handshakeRepeatOnExtra: Set[HandshakeStatus] = Set(HandshakeStatus.NEED_WRAP)

  def apply(
      tls: AtomicBoolean,
      createSSLEngine: () => SSLEngine
  ): scaladsl.BidiFlow[SslTlsOutbound, ByteString, ByteString, SessionBytes, NotUsed] =
    scaladsl.BidiFlow.fromGraph(graph(createSSLEngine, tls))

  private def graph(createSSLEngine: () => SSLEngine, tls: AtomicBoolean): Graph[BidiShape[SslTlsOutbound, ByteString, ByteString, SessionBytes], NotUsed] = {
    scaladsl.GraphDSL.create() { implicit b =>
      implicit val attachment: AtomicReference[Attachment]  = new AtomicReference(Attachment.empty)
      val handshakeBuffer: AtomicReference[Seq[ByteString]] = new AtomicReference(Seq.empty)
      val fromClient: FlowShape[ByteString, SessionBytes]   = fromNetwork(tls, handshakeBuffer)
      val toClient: FlowShape[SslTlsOutbound, ByteString]   = toNetwork(createSSLEngine, tls, handshakeBuffer)
      BidiShape.fromFlows(toClient, fromClient)
    }
  }

  private def fromNetwork(tls: AtomicBoolean, handshakeBuffer: AtomicReference[Seq[ByteString]])(implicit
      b: GraphDSL.Builder[NotUsed],
      attachment: AtomicReference[Attachment]
  ): FlowShape[ByteString, SessionBytes] = {

    b.add(scaladsl.Flow[ByteString].flatMap { bytes =>
      implicit val seq: Int = iterator.next()
      logger.trace(s"From network ($seq): engine=${attachment.get().engine.isDefined} ${bytes.length} ${tls.get()}")
      val list = if (tls.get) {
        val l = attachment.get() match {
          case Attachment(Some(engine), buffers, handshakeStatus, open) =>
            implicit val e: SSLEngine = engine
            val bb                    =
              if (handshakeStatus.get() == HandshakeStatus.NOT_HANDSHAKING || handshakeStatus.get() == HandshakeStatus.FINISHED) {
                unwrapFromNetwork(bytes.asByteBuffer)
              } else {
                doHandshake(buffers, handshakeStatus, open)(
                  BufferAction.copyTo(bytes),
                  b => {
                    logger.trace(s"Add to buffer: $b ${engine.getHandshakeStatus}  -> $handshakeStatus")
                    handshakeBuffer.accumulateAndGet(
                      Seq(ByteString(b)),
                      (prev, next) => {
                        b.position(b.limit)
                        prev ++ next
                      }
                    )

                  }
                )
                val remainingAfterFinished = if (handshakeStatus.get() == HandshakeStatus.FINISHED) {
                  val remaining = buffers.peerNetData.get().flip()
                  unwrapFromNetwork(remaining)
                } else {
                  None
                }
                handshakeBuffer.get().map(_ => SessionBytes(engine.getSession, ByteString(Utils.withEndOfLine(Constants.HANDSHAKE)))) ++ remainingAfterFinished
              }
            bb.iterator.toSeq
          case _ =>
            sys.error("Should not happen")
        }
        l
      } else {
        Seq(SessionBytes(dummySession, bytes))
      }

      Source(list)
    })
  }

  private def unwrapFromNetwork(buffer: ByteBuffer)(implicit seq: Int, engine: SSLEngine): Option[SessionBytes] =
    handleRead(Option(engine))(BufferAction.copyTo(buffer), () => ()) match {
      case (Some(buffer), _) =>
        val bs = ByteString(buffer)
        logger.trace(s"($seq) ${bs.utf8String.trim}")
        Option(SessionBytes(engine.getSession, bs))
      case _ =>
        logger.trace(s"($seq) No data")
        None
    }

  private def toNetwork(createSSLEngine: () => SSLEngine, tls: AtomicBoolean, handshakeBuffer: AtomicReference[Seq[ByteString]])(implicit
      b: GraphDSL.Builder[NotUsed],
      attachment: AtomicReference[Attachment]
  ): FlowShape[SslTlsOutbound, ByteString] =
    b.add(scaladsl.Flow[SslTlsOutbound].flatMap {
      case SendBytes(bytes) =>
        val str = bytes.utf8String.trim
        logger.trace(s"Plain response to network: $tls $str ${attachment.get().engine.map(_.getHandshakeStatus)}")
        val s = if (tls.get) {
          val toNetBytes = attachment.get() match {
            case Attachment(Some(engine), _, handshakeStatus, _) =>
              val buf    = handshakeBuffer.getAndSet(Seq.empty)
              val holder = new AtomicReference[Seq[ByteString]](buf)
              if (handshakeStatus.get == HandshakeStatus.NOT_HANDSHAKING || handshakeStatus.get == HandshakeStatus.FINISHED) {
                if (str != SmtpResponses.HANDSHAKE_RESPONSE) {
                  write(Option(engine), bytes.asByteBuffer)(
                    b => {
                      holder.accumulateAndGet(
                        Seq(ByteString(b)),
                        { (prev, next) =>
                          prev ++ next
                        }
                      )
                    },
                    () => ()
                  )(iterator.next())
                }
              }
              holder.get
            case a =>
              implicit val e: SSLEngine = createSSLEngine()
              val at                    = setEngineModeAndStartHandshake(a, useClientMode = false)
              attachment.set(at)
              Seq(bytes)
          }
          toNetBytes
        } else {
          Seq(bytes)
        }
        logger.trace(s"${s.size}")
        s.foreach { b =>
          logger.trace(s"Sending to network: $str: ${b.length} bytes.")
        }
        if (s.isEmpty) {
          Source.empty[ByteString]
        } else {
          Source(s)
        }
      case x =>
        logger.error(s"$x", new IllegalStateException(s"Unexpected message: $x"))
        Source.empty[ByteString]
    })

}
