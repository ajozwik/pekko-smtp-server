package pl.jozwik.smtp.runtime

import pl.jozwik.smtp.util.{AbstractSpec, TestUtils}

import scala.concurrent.duration.DurationInt
import scala.util.Properties

trait AbstractMainSpec extends AbstractSpec {

  protected def runMain(props: Map[String, String]): Unit = {
    props.foreach { case (k, v) => Properties.setProp(k, v) }

    Main.main(Array.empty)
    TestUtils.waitFor(!Main.r.isBound, 10.millis, s"${Main.r.getClass.getName}")
    Main.r.close()
    props.keys.foreach(Properties.clearProp)
  }

}
