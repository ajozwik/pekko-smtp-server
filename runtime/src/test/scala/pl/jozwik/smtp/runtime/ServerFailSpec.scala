package pl.jozwik.smtp.runtime

import org.apache.pekko.stream.scaladsl.Tcp
import org.scalatest.{ Assertion, BeforeAndAfter, BeforeAndAfterAll }
import pl.jozwik.smtp.{ ActorSpec, SocketSpec }
import pl.jozwik.smtp.server.consumer.{ FileLogConsumer, LogConsumer }
import pl.jozwik.smtp.util.Constants.*
import pl.jozwik.smtp.util.SmtpCodes.*
import pl.jozwik.smtp.util.TestUtils.*
import pl.jozwik.smtp.util.*

import scala.concurrent.Future

class ServerFailFileSpec extends AbstractSmtpServerSpec(FileLogConsumer.consumer)

class ServerFailSpec extends AbstractSmtpServerSpec(LogConsumer.consumer)

abstract class AbstractSmtpServerSpec(consumer: Mail => Future[ConsumedResult])
  extends AbstractAsyncSpec
  with BeforeAndAfter
  with BeforeAndAfterAll
  with SocketSpec
  with ActorSpec {
  lazy val port: Int = TestUtils.notOccupiedPortNumber
  logger.debug(s"PORT=$port $consumer")
  protected val sizeOfMailBody: Int = 10 * 1000

  private lazy val r = new Run((host, port) => Tcp().bind(host, port))(ServerOpts(port, sizeOfMailBody, consumer))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    r.server
    logger.debug(s"${readAnswer(reader)}")
  }

  private def reset() = {
    writeLineAndValidateAnswer(s"$RSET", REQUEST_COMPLETE)
  }

  before {
    reset()
  }

  after {
    reset()
  }

  override protected def afterAll(): Unit = {
    writeLineAndValidateAnswer(s"$QUIT", CLOSING_TERMINATION_CHANNEL)
    r.close()
    close()
    super.afterAll()
  }

  protected def writeLineAndValidateAnswer(line: String, returnCode: Int): Assertion = {
    writeLine(writer, line)
    readAnswer(reader) should startWith(s"$returnCode")

  }

  protected def sendEhlo(): Assertion = {
    writeLineAndValidateAnswer(s"$EHLO localhost", REQUEST_COMPLETE)
  }

  "SmtpServer " should {

    s"$EHLO needed" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:  $OpenBracket   ajozwik@localhost   $CloseBracket SIZE=44", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Handle $MAIL_FROM with spaces in brackets" in {
      sendEhlo()
      writeLineAndValidateAnswer(s"$MAIL_FROM:  $OpenBracket   ajozwik@localhost   $CloseBracket SIZE=44", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$MAIL_FROM:  ${OpenBracket}a@a.pl$CloseBracket SIZE=${sizeOfMailBody - 1}", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Handle $DATA ERROR" in {
      writeLineAndValidateAnswer(s"$DATA", BAD_SEQUENCE_OF_COMMANDS)
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@a.pl$CloseBracket", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$DATA:${OpenBracket}a@a$CloseBracket", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Reject $STARTTLS" in {
      writeLineAndValidateAnswer(s"$STARTTLS", TLS_NOT_SUPPORTED)
    }

    s"Handle $MAIL_FROM with space" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:  a@a.pl SIZE=44", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$MAIL_FROM: a@a.pl SIZE=${sizeOfMailBody - 1}", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Handle $MAIL_FROM size parameter" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@a.pl$CloseBracket SIZE=ty", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$MAIL_FROM:$OpenBracket a@a.pl$CloseBracket SIZE=${sizeOfMailBody - 2}", REQUEST_COMPLETE)
    }

    s"Handle $MAIL_FROM size exceeded" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@a.pl$CloseBracket SIZE=${sizeOfMailBody + 1}", SIZE_EXCEEDS_MAXIMUM)
    }

    s"Handle $MAIL_FROM wrong parameter" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@a.pl$CloseBracket SS=343", PARAMETER_UNRECOGNIZED)
    }

    s"Handle $MAIL_FROM wrong format" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@a.pl$CloseBracket SS", PARAMETER_UNRECOGNIZED)
    }

    s"Wrong format of $MAIL" in {
      writeLineAndValidateAnswer(s"${MAIL}R:${OpenBracket}a@a.pl$CloseBracket", COMMAND_NOT_IMPLEMENTED)
    }

    "White spaces in command " in {
      writeLineAndValidateAnswer(s"       $RSET         ", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"     $MAIL        $FROM:${OpenBracket}a@b.pl$CloseBracket", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"  $VRFY ok@pl", 2)
    }

    s"Handle $MAIL_FROM" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a$CloseBracket", REQUEST_ACTION_NOT_ALLOWED)
      writeLineAndValidateAnswer("", COMMAND_NOT_IMPLEMENTED)
      writeLineAndValidateAnswer(s"$MAIL_FROM:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$MAIL $TO:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$MAIL_FROM ALA MA KOTA:a@m.pl", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$MAIL_FROM:a@m.pl", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$MAIL_FROM:a@m.pl", BAD_SEQUENCE_OF_COMMANDS)

    }

    s"Handle $RCPT_TO wrong syntax " in {
      writeLine(writer, s"$HELO localhost")
      readAnswer(reader)
      writeLineAndValidateAnswer(s"$MAIL_FROM:$OpenBracket${OpenBracket}a@pl$CloseBracket$CloseBracket", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$RCPT FROM:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$RCPT_TO:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$RCPT_TO ALA MA KOTA:a@m.pl", SYNTAX_ERROR)
    }

    s"Handle $RCPT_TO" in {
      writeLine(writer, s"$HELO localhost")
      readAnswer(reader)
      writeLineAndValidateAnswer(s"$NOOP", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$RCPT_TO:${OpenBracket}a$CloseBracket", BAD_SEQUENCE_OF_COMMANDS)
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@pl$CloseBracket", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$NOOP:aa", COMMAND_NOT_IMPLEMENTED)
      writeLineAndValidateAnswer(s"$RCPT_TO:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$RCPT_TO:@a", REQUEST_ACTION_NOT_ALLOWED)
      writeLineAndValidateAnswer(s"$RCPT_TO:${OpenBracket}a$CloseBracket", REQUEST_ACTION_NOT_ALLOWED)

    }

    s"Handle $COMMAND_NOT_IMPLEMENTED error code" in {
      writeLine(writer, "AAAA")
      readAnswer(reader) should startWith(s"$COMMAND_NOT_IMPLEMENTED")
    }

    s"Write line in two part" in {
      writer.print(s"$HELO")
      writer.flush()
      writeLine(writer, "")
      readAnswer(reader) should startWith(s"$REQUEST_COMPLETE")
    }

    s"Handle $DATA " in {
      sendEhlo()
      writeLineAndValidateAnswer(s"$MAIL_FROM:${OpenBracket}a@pl$CloseBracket", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$RCPT_TO:${OpenBracket}a@op$CloseBracket", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$DATA", START_MAIL_INPUT)
      writeLineAndValidateAnswer(END_DATA, REQUEST_COMPLETE)
    }

  }

}
