package pl.jozwik.smtp.client

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Flow, Framing, Source, Tcp }
import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.SmtpUtils
import pl.jozwik.smtp.util.{ Constants, Mail, SmtpCodes, SocketAddress, Utils }

import scala.concurrent.Future

class StreamClient(host: String, port: Int)(implicit system: ActorSystem) extends SenderClient with StrictLogging {

  def this(serverAddress: SocketAddress)(implicit system: ActorSystem) =
    this(serverAddress.host, serverAddress.port)

  private val connection: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
    Tcp().outgoingConnection(host, port)

  def sendMail(mail: Mail): Future[Result] = {
    import Constants.*
    val future = Source
      .single(mail)
      .map { mail =>
        Seq(s"$EHLO ${mail.from.domain}", s"$MAIL_FROM: ${mail.from}") ++
          mail.to.map(to => s"$RCPT_TO:$to") ++
          Seq(s"$DATA", s"$Subject:${mail.emailContent.subject}", "", mail.emailContent.bodyAsString, END_DATA, QUIT)
      }
      .map(seq => ByteString(seq.map(Utils.withEndOfLine).mkString))
      .via(connection)
      .via(Framing.delimiter(ByteString("\n"), Constants.MaximumFrameLength, allowTruncation = true))
      .runFold[(Result, Seq[Int])]((SuccessResult, Seq.empty[Int])) { case ((acc, codes), message) =>
        val response = SmtpUtils.toInt(message.take(3).utf8String)
        logger.debug(s"${message.utf8String}")
        val newAcc = acc match {
          case f: FailedResult =>
            f
          case _ if isResponseSuccess(response) =>
            acc
          case _ =>
            FailedResult((message.utf8String + Constants.Delimiter).stripLineEnd)
        }

        (newAcc, response.map(c => c +: codes).getOrElse(codes))
      }
    mapToResult(future)
  }

  private def mapToResult(future: Future[(Result, Seq[Int])]) = {
    import system.dispatcher
    future
      .map {
        case (SuccessResult, codes) if !codes.containsSlice(Seq(SmtpCodes.REQUEST_COMPLETE, SmtpCodes.START_MAIL_INPUT)) =>
          FailedResult("")
        case (result, _) =>
          result

      }
      .recover { case e: Throwable =>
        logger.error("", e)
        FailedResult(e.getMessage)
      }
  }

  private def isResponseSuccess(response: Option[Int]) = {
    response.exists(r => r >= 200 && r < 400)
  }

}
