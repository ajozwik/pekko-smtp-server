package pl.jozwik.smtp

import org.apache.pekko.pattern.*
import pl.jozwik.smtp.server.ActorWithTimeout.TimeoutTick

class UnhandledSpec extends AbstractWithActorSystemSpec {

  private val ref = actorSystem.actorOf(UnhandledActor.props)

  "Unhandled" should {
    "Handle " in {
      ref ! 2
      for { r <- ref ? TimeoutTick } yield {
        r shouldBe UnhandledActor.TimeoutResponse
      }

    }
  }

}
