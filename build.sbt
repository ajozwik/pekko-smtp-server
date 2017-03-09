import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtScalariform.autoImport._

import scalariform.formatter.preferences.{AlignSingleLineCaseStatements, SpacesAroundMultiImports}

import com.banno.license.Plugin.LicenseKeys._
import com.banno.license.Licenses._



val scalaVersion2_11 = "2.11.8"

val scalaVersion2_12 = "2.12.1"

crossScalaVersions := Seq(scalaVersion2_12, scalaVersion2_11)

scalaVersion in ThisBuild := scalaVersion2_11

organization in ThisBuild := "com.github.ajozwik"

name := "akka-smtp-server"

scalacOptions in ThisBuild ++= Seq(
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code",
  "-language:reflectiveCalls",
  "-Ybackend:GenBCode",
  "-Ydelambdafy:method"
)

val akkaVersion = "2.4.17"

val `com.typesafe.scala-logging_scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"

val `ch.qos.logback_logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.1"

val `com.typesafe.akka_akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

val `com.typesafe.akka_stream` = "com.typesafe.akka" %% "akka-stream" % akkaVersion

val `org.scalatest_scalatest` = "org.scalatest" %% "scalatest" % "3.0.1" % "test"

val `org.scalacheck_scalacheck` = "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"

val `org.apache.james_apache-mime4j` = "org.apache.james" % "apache-mime4j" % "0.8.0"

lazy val `smtp-util` = projectName("smtp-util", file("smtp-util")).settings(
  libraryDependencies ++= Seq(
    `ch.qos.logback_logback-classic`,
    `com.typesafe.scala-logging_scala-logging`,
    `org.apache.james_apache-mime4j`
  )
)

lazy val `akka-smtp` = projectName("akka-smtp", file("akka-smtp")).settings(
  libraryDependencies ++= Seq(
    `com.typesafe.akka_akka-slf4j`,
    `com.typesafe.akka_stream` 
  )
).dependsOn(`smtp-util`, `smtp-util` % "test->test")

def projectName(name: String, file: File): Project = Project(name, file).settings(
  libraryDependencies ++= Seq(
    `org.scalatest_scalatest`,
    `org.scalacheck_scalacheck`
  ),
  licenseSettings,
  removeExistingHeaderBlock := true,
  license := mit("Copyright (c) 2017 Andrzej Jozwik"),
    SbtScalariform.scalariformSettings,
  publishArtifact in(Compile, packageDoc) := false,
  sources in(Compile, doc) := Seq.empty,
  scalariformSettings,
  scapegoatVersion in Scapegoat := "1.3.0",
  scapegoatIgnoredFiles := Seq(".*/target/.*"),
  ScalariformKeys.preferences := ScalariformKeys.preferences.value.
    setPreference(AlignSingleLineCaseStatements, true).
    setPreference(SpacesAroundMultiImports, false)
)


publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  }
  else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

licenses in ThisBuild := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

pomExtra in ThisBuild :=
  <url>https://github.com/ajozwik/akka-smtp-server</url>
    <licenses>
      <license>
        <name>MIT License</name>
        <url>http://www.opensource.org/licenses/mit-license.php</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:ajozwik/akka-smtp-server.git</url>
      <connection>scm:git:git@github.com:ajozwik/akka-smtp-server.git</connection>
    </scm>
    <developers>
      <developer>
        <id>ajozwik</id>
        <name>Andrzej Jozwik</name>
        <url>https://github.com/ajozwik</url>
      </developer>
    </developers>


