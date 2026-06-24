package pl.jozwik.smtp

import org.apache.pekko.actor.Props

object AlwaysFailActor {
  def props: Props = Props[AlwaysFailActor]()
}

class AlwaysFailActor extends AbstractActor {

  def receive: Receive = { case _ =>
    sys.error("Always failed")
  }

}
