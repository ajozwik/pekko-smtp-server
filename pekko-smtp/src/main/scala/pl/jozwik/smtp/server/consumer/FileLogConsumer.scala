package pl.jozwik.smtp.server.consumer

import java.io.{ File, PrintWriter }
import java.util.concurrent.atomic.AtomicInteger

import pl.jozwik.smtp.util.{ ConsumedResult, Mail, SuccessfulConsumed }

import scala.concurrent.Future

object FileLogConsumer extends FileLogConsumer

class FileLogConsumer extends AbstractConsumer {

  private val tmpDir = new File(System.getProperty("java.io.tmpdir"))
  private val counter = new AtomicInteger(0)

  override def consumer(mail: Mail): Future[ConsumedResult] = {
    storeToFile(mail)
    Future.successful(SuccessfulConsumed)
  }

  private def storeToFile(mail: Mail): Unit = {
    val fileName = s"${mail.from}_${counter.incrementAndGet()}"
    Option(new PrintWriter(new File(tmpDir, fileName))).foreach { p =>
      p.write(mail.toString)
      p.close()
    }
  }
}