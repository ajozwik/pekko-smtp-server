import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._
import scalariform.formatter.preferences._

val `scalaVersion_2.13` = "2.13.0"

val `scalaVersion_2.12` = "2.12.9"

ThisBuild / scapegoatVersion := "1.3.10"

crossScalaVersions := Seq(`scalaVersion_2.13`, `scalaVersion_2.12`)

ThisBuild / scalaVersion := `scalaVersion_2.12`

ThisBuild / organization := "com.github.ajozwik"

name := "akka-smtp-server"

ThisBuild / scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-encoding",
  "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  //  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  //  "-Ywarn-inaccessible",
  "-Ywarn-dead-code",
  "-language:reflectiveCalls",
  "-Ydelambdafy:method"
)

val akkaVersion = "2.5.25"

val `com.typesafe.scala-logging_scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

val `ch.qos.logback_logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3"

val `com.typesafe.akka_akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

val `com.typesafe.akka_stream` = "com.typesafe.akka" %% "akka-stream" % akkaVersion

val `org.scalatest_scalatest` = "org.scalatest" %% "scalatest" % "3.0.8" % Test

val `org.scalacheck_scalacheck` = "org.scalacheck" %% "scalacheck" % "1.14.0" % Test

val `org.apache.james_apache-mime4j` = "org.apache.james" % "apache-mime4j" % "0.8.3"

lazy val `smtp-util` = projectName("smtp-util", file("smtp-util")).settings(
  libraryDependencies ++= Seq(
    `ch.qos.logback_logback-classic`,
    `com.typesafe.scala-logging_scala-logging`,
    `org.apache.james_apache-mime4j`
  )
)

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
      `org.scalacheck_scalacheck`
    ),
    licenseReportTitle := "Copyright (c) 2019 Andrzej Jozwik",
    licenseSelection := Seq(LicenseCategory.MIT),
    sources in (Compile, doc) := Seq.empty,
    scalariformPreferences := scalariformPreferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(DanglingCloseParenthesis, Preserve)
  )
