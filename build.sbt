import java.time.LocalDate
import Versions.*

val scalaVersion_3_lts  = "3.3.8"
val `scalaVersion_2.13` = "2.13.18"

crossScalaVersions := Seq(`scalaVersion_2.13`, scalaVersion_3_lts)

scalaVersion := sys.props.getOrElse("scala.version", scalaVersion_3_lts)

organization := "com.github.ajozwik"

name := "pekko-smtp-server"

val targetJdk = "17"

scalafixDependencies += "com.github.vovapolu" %% "scaluzzi" % "0.1.23"

libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  s"-release:$targetJdk"
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, _)) =>
    Seq(
      "-Ycache-plugin-class-loader:last-modified",
      "-Ycache-macro-class-loader:last-modified",
      "-Ywarn-dead-code",
      "-Xlint",
      "-language:_",
      "-Yrangepos",
      "-Xsource:3",
      "-Xlint:-byname-implicit",
      "-Ymacro-annotations",
      "-Xmaxwarns",
      200.toString
    )
  case _ =>
    Seq(
      "-Wunused:imports",
      "-Wunused:linted",
      "-Wunused:locals",
      "-Wunused:params",
      "-Wunused:privates",
      "-language:implicitConversions"
    )
})

val wartConfig = Warts.allBut(
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

publish / skip := true

val `ch.qos.logback_logback-classic`           = "ch.qos.logback"              % "logback-classic" % "1.6.1"
val `com.typesafe.scala-logging_scala-logging` = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.6"
val `org.apache.james_apache-mime4j`           = "org.apache.james"            % "apache-mime4j"   % "0.8.14"
val `org.apache.pekko_akka-slf4j`              = "org.apache.pekko"           %% "pekko-slf4j"     % pekkoVersion
val `org.apache.pekko_stream`                  = "org.apache.pekko"           %% "pekko-stream"    % pekkoVersion
val `org.scalatest_scalatest`                  = "org.scalatest"              %% "scalatest"       % scalatestVersion       % Test
val `org.scalatestplus_scalacheck`             = "org.scalatestplus"          %% "scalacheck-1-19" % s"$scalatestVersion.0" % Test

lazy val `smtp-util` = projectName("smtp-util", file("smtp-util")).settings(
  libraryDependencies ++= Seq(
    `ch.qos.logback_logback-classic`,
    `com.typesafe.scala-logging_scala-logging`,
    `org.apache.pekko_stream`,
    `org.apache.james_apache-mime4j`
  )
)

lazy val `runtime` = projectName("runtime", file("runtime"), true)
  .dependsOn(`pekko-smtp`)
  .dependsOn(Seq(`smtp-util`, `pekko-smtp`).map(_ % "test->test") *)
  .enablePlugins(PackPlugin)

lazy val `pekko-smtp` = projectName("pekko-smtp", file("pekko-smtp"))
  .settings(
    libraryDependencies ++= Seq(
      `org.apache.pekko_akka-slf4j`
    )
  )
  .dependsOn(`smtp-util`, `smtp-util` % "test->test")

lazy val docs = project
  .in(file("smtp-docs"))
  .settings(
    name          := "smtp-docs",
    mdocVariables := Map("VERSION" -> version.value)
  )
  .dependsOn(runtime)
  .enablePlugins(MdocPlugin)

def projectName(id: String, file: File, skipPublish: Boolean = false): Project =
  Project(id, file).settings(
    name           := id,
    publish / skip := skipPublish,
    libraryDependencies ++= Seq(
      `org.scalatest_scalatest`,
      `org.scalatestplus_scalacheck`
    ),
    licenseReportTitle       := s"Copyright (c) ${LocalDate.now.getYear} Andrzej Jozwik",
    licenseSelection         := Seq(LicenseCategory.MIT),
    Compile / doc / sources  := Seq.empty,
    Test / parallelExecution := false,
    Compile / compile / wartremoverWarnings ++= wartConfig
  )
