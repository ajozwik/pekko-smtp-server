import java.time.LocalDate

import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._

val `scalaVersion_2.13` = "2.13.11"

ThisBuild / scapegoatVersion := "2.1.2"

crossScalaVersions := Seq(`scalaVersion_2.13`)

ThisBuild / scalaVersion := sys.props.getOrElse("scala.version", `scalaVersion_2.13`)

ThisBuild / organization := "com.github.ajozwik"

name := "akka-smtp-server"

val targetJdk = "1.8"

ThisBuild / scalafixDependencies += "com.github.vovapolu" %% "scaluzzi" % "0.1.23"

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

ThisBuild / scalacOptions ++= Seq(
  s"-target:jvm-$targetJdk",
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  //  "-Ywarn-adapted-args",
  "-Ywarn-value-discard",
  //  "-Ywarn-inaccessible",
  "-Ywarn-dead-code",
  "-language:reflectiveCalls",
  "-Ydelambdafy:method",
  "-language:postfixOps"
)

publish / skip := true

val akkaVersion = "2.6.20"

val scalatestVersion = "3.2.16"

val `ch.qos.logback_logback-classic`           = "ch.qos.logback"              % "logback-classic" % "1.2.12"
val `com.typesafe.akka_akka-slf4j`             = "com.typesafe.akka"          %% "akka-slf4j"      % akkaVersion
val `com.typesafe.akka_stream`                 = "com.typesafe.akka"          %% "akka-stream"     % akkaVersion
val `com.typesafe.scala-logging_scala-logging` = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5"
val `org.apache.james_apache-mime4j`           = "org.apache.james"            % "apache-mime4j"   % "0.8.9"
val `org.scalatest_scalatest`                  = "org.scalatest"              %% "scalatest"       % scalatestVersion       % Test
val `org.scalatestplus_scalacheck-1-15`        = "org.scalatestplus"          %% "scalacheck-1-17" % s"$scalatestVersion.0" % Test

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
    licenseReportTitle       := s"Copyright (c) ${LocalDate.now.getYear} Andrzej Jozwik",
    licenseSelection         := Seq(LicenseCategory.MIT),
    Compile / doc / sources  := Seq.empty,
    Test / parallelExecution := false
  )
