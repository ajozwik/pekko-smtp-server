package pl.jozwik.smtp.runtime

@SuppressWarnings(Array("org.wartremover.warts.ScalaApp"))
object Main extends App {

  private val r = new Run(ServerOpts.fromSystemProps)
  r.server

}
