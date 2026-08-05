package pl.jozwik.smtp

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.util.Constants.*

import scala.util.Using

trait Demo extends WithNioSslClient with StrictLogging {

  protected def sendMail(name: String)(port: Int): Unit =
    Using.resource(
      createClient(name)(port)
    ) { implicit client =>
      client.connect()
      val banner = client.waitForRead()
      logger.trace(s"Banner: ${toMessage(banner)}")
      val ehloResponse = writeAndWaitForRead(s"$EHLO $name!")
      logger.trace(s"$EHLO Response ${toMessage(ehloResponse)}")
      val tlsResponse = client.startTls()
      logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
      val bb = writeAndWaitForRead(s"$EHLO again_$name!")
      logger.trace(s"${toMessage(bb)}")
      writeAndWaitForRead(s"$NOOP")
      val mailResponse = writeAndWaitForRead(s"$MAIL_FROM: aa@aa.pl")
      logger.trace(s"$MAIL_FROM ${toMessage(mailResponse)}")
      val rcptResponse = writeAndWaitForRead(s"$RCPT_TO: aa@aa.pl")
      logger.trace(s"$RCPT_TO ${toMessage(rcptResponse)}")
      val dataResponse = writeAndWaitForRead(s"$DATA")
      logger.trace(s"$DATA ${toMessage(dataResponse)}")
      val message = writeAndWaitForRead(s"""Ala ma kota
          |I co z tego?
          |$END_DATA""".stripMargin)
      logger.trace(s"${toMessage(message)}")
      val quitResponse = writeAndWaitForRead(s"$QUIT")
      logger.trace(s"${toMessage(quitResponse)}")
    }

}
