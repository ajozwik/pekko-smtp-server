import java.time.LocalDate

import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._

val `scalaVersion_2.13` = "2.13.10"

ThisBuild / scapegoatVersion := "2.0.0"

crossScalaVersions := Seq(`scalaVersion_2.13`)

ThisBuild / scalaVersion := sys.props.getOrElse("scala.version", `scalaVersion_2.13`)

ThisBuild / organization := "com.github.ajozwik"

name := "akka-smtp-server"

val targetJdk = "11"

ThisBuild / wartremoverWarnings ++= Warts.allBut(
  Wart.Any,
  Wart.DefaultArguments,
  Wart.Enumeration,
  Wart.Equals,
  Wart.ImplicitConversion,
  Wart.ImplicitParameter,
  Wart.JavaSerializable,
  Wart.NonUnitStatements,
  Wart.Nothing,
  Wart.Overloading,
  Wart.StringPlusAny,
  Wart.ToString,
  Wart.Throw
)

ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature",     // warning and location for usages of features that should be imported explicitly
  "-unchecked",   // additional warnings where generated code depends on assumptions
  "-Xlint",       // recommended additional warnings
  //  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  //  "-Ywarn-inaccessible",
  "-Ywarn-dead-code",
  "-language:reflectiveCalls",
  "-Ydelambdafy:method",
  s"-release:$targetJdk",
  "-Xsource:3"
)

publish / skip := true

val akkaVersion = "2.7.0"

val scalatestVersion = "3.2.15"

val `com.typesafe.scala-logging_scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"

val `ch.qos.logback_logback-classic` = "ch.qos.logback" % "logback-classic" % "1.3.5"

val `com.typesafe.akka_akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

val `com.typesafe.akka_stream` = "com.typesafe.akka" %% "akka-stream" % akkaVersion

val `org.scalatest_scalatest` = "org.scalatest" %% "scalatest" % scalatestVersion % Test

val `org.scalatestplus_scalacheck-1-15` = "org.scalatestplus" %% "scalacheck-1-17" % s"$scalatestVersion.0" % Test

val `org.apache.james_apache-mime4j` = "org.apache.james" % "apache-mime4j" % "0.8.7"

lazy val `smtp-util` = projectName("smtp-util", file("smtp-util")).settings(
  libraryDependencies ++= Seq(
    `ch.qos.logback_logback-classic`,
    `com.typesafe.scala-logging_scala-logging`,
    `org.apache.james_apache-mime4j`
  )
)

lazy val `runtime` = projectName("runtime", file("runtime"))
  .settings(publish / skip := true)
  .dependsOn(`akka-smtp`)
  .dependsOn(Seq(`smtp-util`, `akka-smtp`).map(_ % "test->test"): _*)

lazy val `akka-smtp` = projectName("akka-smtp", file("akka-smtp"))
  .settings(
    libraryDependencies ++= Seq(
      `com.typesafe.akka_akka-slf4j`,
      `com.typesafe.akka_stream`
    )
  )
  .dependsOn(`smtp-util`, `smtp-util` % "test->test")
  .enablePlugins(PackPlugin)

def projectName(name: String, file: File): Project =
  Project(name, file).settings(
    libraryDependencies ++= Seq(
      `org.scalatest_scalatest`,
      `org.scalatestplus_scalacheck-1-15`
    ),
    licenseReportTitle := s"Copyright (c) ${LocalDate.now.getYear} Andrzej Jozwik",
    licenseSelection := Seq(LicenseCategory.MIT),
    Compile / doc / sources := Seq.empty,
    Test / parallelExecution := false
  )

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
