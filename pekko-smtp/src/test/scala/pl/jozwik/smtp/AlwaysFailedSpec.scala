package pl.jozwik.smtp

import java.util.concurrent.TimeoutException

import org.apache.pekko.pattern.*
import org.scalatest.compatible.Assertion

class AlwaysFailedSpec extends AbstractActorSpec {

  private val failedRef = actorSystem.actorOf(AlwaysFailActor.props)

  "Always failed" should {
    "Failed " in {

      val future = failedRef ? "FAIL"
      future.recover { case e => e shouldBe a[TimeoutException] }.mapTo[Assertion]

    }
  }

}
