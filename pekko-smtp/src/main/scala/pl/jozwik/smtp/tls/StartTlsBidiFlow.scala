package pl.jozwik.smtp.tls

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.TLSProtocol.{SendBytes, SessionBytes, SslTlsOutbound}
import org.apache.pekko.stream.scaladsl.{GraphDSL, Source}
import org.apache.pekko.stream.{BidiShape, FlowShape, Graph, scaladsl}
import org.apache.pekko.util.ByteString
import pl.jozwik.smtp.util.{ByteBufferHelper, Constants, SmtpResponses, Utils}

import java.nio.ByteBuffer
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.{SSLContext, SSLEngine, SSLSession}

object StartTlsBidiFlow {
  private val dummySession: SSLSession = SSLContext.getDefault.createSSLEngine.getSession

  def apply(
      tls: AtomicBoolean,
      createSSLEngine: () => SSLEngine,
      whoIAm: String
  ): scaladsl.BidiFlow[SslTlsOutbound, ByteString, ByteString, SessionBytes, NotUsed] =
    scaladsl.BidiFlow.fromGraph(graph(createSSLEngine, tls, whoIAm))

  private def graph(
      createSSLEngine: () => SSLEngine,
      tls: AtomicBoolean,
      whoIAm: String
  ): Graph[BidiShape[SslTlsOutbound, ByteString, ByteString, SessionBytes], NotUsed] = {
    scaladsl.GraphDSL.create() { implicit b =>
      implicit val attachment: AtomicReference[Attachment]  = new AtomicReference(Attachment.empty)
      implicit val open: AtomicBoolean                      = new AtomicBoolean(true)
      val handshakeBuffer: AtomicReference[Seq[ByteString]] = new AtomicReference(Seq.empty)
      val bidiFlow                                          = new StartTlsBidiFlow(whoIAm)
      val fromClient: FlowShape[ByteString, SessionBytes]   = bidiFlow.fromNetwork(tls, handshakeBuffer)
      val toClient: FlowShape[SslTlsOutbound, ByteString]   = bidiFlow.toNetwork(createSSLEngine, tls, handshakeBuffer)
      BidiShape.fromFlows(toClient, fromClient)
    }
  }

}

class StartTlsBidiFlow(protected val whoIAm: String) extends WithSslEngineServer {
  import StartTlsBidiFlow.dummySession
  protected val (applicationBufferSize, packetBufferSize)             = (dummySession.getApplicationBufferSize, dummySession.getPacketBufferSize)
  protected override def handshakeRepeatOnExtra: Set[HandshakeStatus] = Set(HandshakeStatus.NEED_WRAP)

  private def fromNetwork(tls: AtomicBoolean, handshakeBuffer: AtomicReference[Seq[ByteString]])(implicit
      b: GraphDSL.Builder[NotUsed],
      attachment: AtomicReference[Attachment],
      open: AtomicBoolean
  ): FlowShape[ByteString, SessionBytes] = {
    implicit val underflowBuffer: AtomicReference[ByteBuffer] = attachment.get().buffers.underflowBuffer
    b.add(scaladsl.Flow[ByteString].flatMap { bytes =>
      implicit val seq: Int = iterator.next()
      logger.trace(s"fromNetwork ($seq) ${bytes.length}")
      val list = if (tls.get) {
        val l = attachment.get() match {
          case a @ Attachment(Some(engine), buffers, handshakeStatus, _) =>
            implicit val e: SSLEngine = engine
            val bb                    =
              if (handshakeStatus.get() == HandshakeStatus.NOT_HANDSHAKING || handshakeStatus.get() == HandshakeStatus.FINISHED) {
                unwrapFromNetwork(ByteBufferHelper.toByteBufferFlip(bytes), handshakeBuffer)
              } else {
                doHandshake(a)(
                  BufferAction.copyTo(bytes),
                  addToHandshakeBuffer(handshakeBuffer),
                  Utils.fakeCall
                )
                val remainingAfterFinished = if (handshakeStatus.get() == HandshakeStatus.FINISHED) {
                  val remaining = buffers.peerNetData.get()
                  if (remaining.remaining() == 0) {
                    None
                  } else {
                    unwrapFromNetwork(remaining, handshakeBuffer)
                  }
                } else {
                  None
                }
                handshakeBuffer.get().map(_ => SessionBytes(engine.getSession, ByteString(Utils.withEndOfLine(Constants.HANDSHAKE)))) ++ remainingAfterFinished
              }
            bb.iterator.toSeq
          // $COVERAGE-OFF$should never happen unless someone mess around with type-level representation
          case _ =>
            sys.error("Should not happen")
          // $COVERAGE-ON$
        }
        l
      } else {
        Seq(SessionBytes(dummySession, bytes))
      }

      Source(list)
    })
  }

  private def addToHandshakeBuffer(handshakeBuffer: AtomicReference[Seq[ByteString]])(
      b: ByteBuffer
  ): Unit = {
    handshakeBuffer.accumulateAndGet(
      Seq(ByteString(b)),
      (prev, next) => {
        b.position(b.limit)
        prev ++ next
      }
    )
    ()
  }

  private def unwrapFromNetwork(buffer: ByteBuffer, handshakeBuffer: AtomicReference[Seq[ByteString]])(implicit
      seq: Int,
      engine: SSLEngine,
      underflowBuffer: AtomicReference[ByteBuffer],
      open: AtomicBoolean
  ): Option[SessionBytes] = {
    implicit val en: Option[SSLEngine] = Option(engine)

    handleRead(buffer)(ByteBufferHelper.fakeRead, addToHandshakeBuffer(handshakeBuffer), Utils.fakeCall) match {
      case (Some(buffer), _) =>
        val bs = ByteBufferHelper.toByteString(buffer)
        Option(SessionBytes(engine.getSession, bs))
      case _ =>
        None
    }
  }

  private def toNetwork(createSSLEngine: () => SSLEngine, tls: AtomicBoolean, handshakeBuffer: AtomicReference[Seq[ByteString]])(implicit
      b: GraphDSL.Builder[NotUsed],
      attachment: AtomicReference[Attachment]
  ): FlowShape[SslTlsOutbound, ByteString] =
    b.add(scaladsl.Flow[SslTlsOutbound].flatMap {
      case SendBytes(bytes) =>
        val str = bytes.utf8String.trim
        val s   = if (tls.get) {
          val toNetBytes = attachment.get() match {
            case Attachment(Some(engine), _, handshakeStatus, _) =>
              val buf    = handshakeBuffer.getAndSet(Seq.empty)
              val holder = new AtomicReference[Seq[ByteString]](buf)
              if (handshakeStatus.get == HandshakeStatus.NOT_HANDSHAKING || handshakeStatus.get == HandshakeStatus.FINISHED) {
                if (str != SmtpResponses.HANDSHAKE_RESPONSE) {
                  write(bytes.asByteBuffer)(
                    ByteBufferHelper.fakeRead,
                    b => {
                      holder.accumulateAndGet(
                        Seq(ByteString(b)),
                        { (prev, next) =>
                          prev ++ next
                        }
                      )
                    },
                    Utils.fakeCall
                  )(iterator.next(), Option(engine))
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
        if (s.isEmpty) {
          Source.empty[ByteString]
        } else {
          val toSend = s.map(_.length)
          logger.trace(s"$whoIAm To network ${toSend.mkString(", ")}, total: ${toSend.sum} bytes")
          Source(s)
        }

      case x =>
        logger.error(s"$x", new IllegalStateException(s"Unexpected message: $x"))
        Source.empty[ByteString]
    })

}
