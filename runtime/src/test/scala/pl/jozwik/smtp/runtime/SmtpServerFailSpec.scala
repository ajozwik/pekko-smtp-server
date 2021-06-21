/*
 * Copyright (c) 2017 Andrzej Jozwik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pl.jozwik.smtp.runtime

import org.scalatest.{ Assertion, BeforeAndAfter, BeforeAndAfterAll }
import pl.jozwik.smtp.SocketSpec
import pl.jozwik.smtp.server.consumer.{ FileLogConsumer, LogConsumer }
import pl.jozwik.smtp.util.Constants._
import pl.jozwik.smtp.util.TestUtils._
import pl.jozwik.smtp.util._

class SmtpServerFailFileSpec extends AbstractSmtpServerSpec {
  System.setProperty(RuntimeConstants.consumerClass, classOf[FileLogConsumer].getName)
}

class SmtpServerFailSpec extends AbstractSmtpServerSpec {
  System.setProperty(RuntimeConstants.consumerClass, LogConsumer.getClass.getName)
}

abstract class AbstractSmtpServerSpec extends AbstractAsyncSpec with BeforeAndAfter with BeforeAndAfterAll with SocketSpec {
  val port: Int = TestUtils.notOccupiedPortNumber

  protected val sizeOfMailBody: Int = 10 * 1000
  System.setProperty(RuntimeConstants.portKey, port.toString)
  System.setProperty(RuntimeConstants.sizeKey, sizeOfMailBody.toString)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Main.main(Array())
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
    socket.close()
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
      writeLineAndValidateAnswer(s"$MAIL_FROM:  <   ajozwik@localhost   > SIZE=44", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Handle $MAIL_FROM with spaces in brackets" in {
      sendEhlo()
      writeLineAndValidateAnswer(s"$MAIL_FROM:  <   ajozwik@localhost   > SIZE=44", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$MAIL_FROM:  <a@a.pl> SIZE=${sizeOfMailBody - 1}", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Handle $DATA ERROR" in {
      writeLineAndValidateAnswer(s"$DATA", BAD_SEQUENCE_OF_COMMANDS)
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@a.pl>", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$DATA:<a@a>", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Reject $STARTTLS" in {
      writeLineAndValidateAnswer(s"$STARTTLS", TLS_NOT_SUPPORTED)
    }

    s"Handle $MAIL_FROM with space" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:  a@a.pl SIZE=44", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$MAIL_FROM: a@a.pl SIZE=${sizeOfMailBody - 1}", BAD_SEQUENCE_OF_COMMANDS)
    }

    s"Handle $MAIL_FROM size parameter" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@a.pl> SIZE=ty", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$MAIL_FROM:< a@a.pl> SIZE=${sizeOfMailBody - 2}", REQUEST_COMPLETE)
    }

    s"Handle $MAIL_FROM size exceeded" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@a.pl> SIZE=${sizeOfMailBody + 1}", SIZE_EXCEEDS_MAXIMUM)
    }

    s"Handle $MAIL_FROM wrong parameter" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@a.pl> SS=343", PARAMETER_UNRECOGNIZED)
    }

    s"Handle $MAIL_FROM wrong format" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@a.pl> SS", PARAMETER_UNRECOGNIZED)
    }

    s"Wrong format of $MAIL" in {
      writeLineAndValidateAnswer(s"${MAIL}R:<a@a.pl>", COMMAND_NOT_IMPLEMENTED)
    }

    "White spaces in command " in {
      writeLineAndValidateAnswer(s"       $RSET         ", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"     $MAIL        $FROM:<a@b.pl>", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"  $VRFY ok@pl", 2)
    }

    s"Handle $MAIL_FROM" in {
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a>", REQUEST_ACTION_NOT_ALLOWED)
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
      writeLineAndValidateAnswer(s"$MAIL_FROM:<<a@pl>>", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$RCPT FROM:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$RCPT_TO:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$RCPT_TO ALA MA KOTA:a@m.pl", SYNTAX_ERROR)
    }

    s"Handle $RCPT_TO" in {
      writeLine(writer, s"$HELO localhost")
      readAnswer(reader)
      writeLineAndValidateAnswer(s"$NOOP", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$RCPT_TO:<a>", BAD_SEQUENCE_OF_COMMANDS)
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@pl>", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$NOOP:aa", COMMAND_NOT_IMPLEMENTED)
      writeLineAndValidateAnswer(s"$RCPT_TO:", SYNTAX_ERROR)
      writeLineAndValidateAnswer(s"$RCPT_TO:@a", REQUEST_ACTION_NOT_ALLOWED)
      writeLineAndValidateAnswer(s"$RCPT_TO:<a>", REQUEST_ACTION_NOT_ALLOWED)

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
      writeLineAndValidateAnswer(s"$MAIL_FROM:<a@pl>", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$RCPT_TO:<a@op>", REQUEST_COMPLETE)
      writeLineAndValidateAnswer(s"$DATA", START_MAIL_INPUT)
      writeLineAndValidateAnswer(END_DATA, REQUEST_COMPLETE)
    }

  }

}
