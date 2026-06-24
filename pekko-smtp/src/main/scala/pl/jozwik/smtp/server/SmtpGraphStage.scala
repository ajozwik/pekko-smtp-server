package pl.jozwik.smtp.server

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream
import org.apache.pekko.stream.stage.*
import org.apache.pekko.stream.FlowShape
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.*

import scala.concurrent.Future
import scala.concurrent.duration.*

class SmtpGraphStage(
    addressHandler: AddressHandler,
    sizeHandler: SizeParameterHandler,
    localHostName: String,
    consumer: Mail => Future[ConsumedResult],
    readTimeout: FiniteDuration,
    tls: AtomicBoolean
)(remote: InetSocketAddress)(implicit system: ActorSystem)
  extends GraphStage[stream.FlowShape[String, String]]
  with StrictLogging {

  override val shape: FlowShape[String, String] = {
    val in  = stream.Inlet[String]("smtp.in")
    val out = stream.Outlet[String]("smtp.out")
    stream.FlowShape(in, out)
  }

  override def createLogic(inheritedAttributes: stream.Attributes): GraphStageLogic =
    new SmtpTimerGraphStageLogic(shape, addressHandler, sizeHandler, localHostName, remote, consumer, readTimeout, tls)

}
