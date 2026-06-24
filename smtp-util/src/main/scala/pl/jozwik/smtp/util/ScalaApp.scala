package pl.jozwik.smtp.util

import com.typesafe.scalalogging.StrictLogging

@SuppressWarnings(Array("org.wartremover.warts.ScalaApp"))
trait ScalaApp extends App

trait ScalaAppWithLogger extends ScalaApp with StrictLogging
