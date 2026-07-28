package pl.jozwik.smtp.util

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.EitherValues
import org.scalatest.concurrent.{AsyncTimeLimitedTests, TimeLimitedTests}
import org.scalatestplus.scalacheck.Checkers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.{AnyWordSpecLike, AsyncWordSpecLike}
import org.scalatest.matchers.should.Matchers

trait AbstractSpecScalaCheck extends AbstractSpec with Checkers

trait Spec extends StrictLogging {
  val TimeoutSeconds                     = 60
  val timeLimit: Span                    = Span(TimeoutSeconds, Seconds)
  protected val mailAddress: MailAddress = MailAddress("ajozwik", "tuxedo-wifi")
}

trait AbstractSpec extends AnyWordSpecLike with TimeLimitedTests with Spec with Matchers with EitherValues

trait AbstractAsyncSpec extends AsyncWordSpecLike with AsyncTimeLimitedTests with Spec with Matchers with EitherValues
