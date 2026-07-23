package pl.jozwik.smtp

import com.typesafe.scalalogging.StrictLogging
import pl.jozwik.smtp.DemoHelper.{TlsVersion, keyStoreClientInputStream, trustStoreInputStream}
import pl.jozwik.smtp.tls.NioSslClient
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.{ByteBufferHelper, Utils}

import java.nio.ByteBuffer
import scala.util.Using

trait Demo extends StrictLogging {

  private def toMessage(b: ByteBuffer): String = ByteBufferHelper.toString(b).trim

  protected def sendMail(name: String)(port: Int): Unit =
    Using.resource(
      new NioSslClient(TlsVersion, "localhost", port, name, keyStoreClientInputStream, TlsOpts.clientKeystorePassword, TlsOpts.clientKeystorePassword)(
        trustStoreInputStream,
        TlsOpts.trustPassword
      )
    ) { implicit client =>
      client.connect()
      val ehloResponse = writeAndWaitForRead(s"EHLO $name!")
      logger.trace(s"${toMessage(ehloResponse)}")
      val tlsResponse = client.startTls()
      logger.trace(s"TLS response: ${toMessage(tlsResponse)}")
      val bb = writeAndWaitForRead(s"$EHLO again_$name!")
      logger.trace(s"${toMessage(bb)}")
      val mailResponse = writeAndWaitForRead(s"$MAIL_FROM: aa@aa.pl")
      logger.trace(s"${toMessage(mailResponse)}")
      val rcptResponse = writeAndWaitForRead(s"$RCPT_TO: aa@aa.pl")
      logger.trace(s"${toMessage(rcptResponse)}")
      val dataResponse = writeAndWaitForRead(s"$DATA")
      logger.trace(s"${toMessage(dataResponse)}")
      val message = writeAndWaitForRead(s"""Ala ma kota
          |I co z tego?
          |$END_DATA""".stripMargin)
      logger.trace(s"${toMessage(message)}")
      val quitResponse = writeAndWaitForRead(s"$QUIT")
      logger.trace(s"${toMessage(quitResponse)}")
    }

  private def writeAndWaitForRead(txt: String)(implicit client: NioSslClient): ByteBuffer =
    client.writeAndWaitForRead(Utils.withEndOfLine(txt))

}
