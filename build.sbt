import java.time.LocalDate

val `scalaVersion_3`    = "3.3.1"
val `scalaVersion_2.13` = "2.13.11"

crossScalaVersions := Seq(`scalaVersion_2.13`, `scalaVersion_3`)

ThisBuild / scalaVersion := sys.props.getOrElse("scala.version", `scalaVersion_3`)

ThisBuild / organization := "com.github.ajozwik"

name := "pekko-smtp-server"

val targetJdk = "8"

ThisBuild / scalafixDependencies += "com.github.vovapolu" %% "scaluzzi" % "0.1.23"

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:_",
  s"-release:$targetJdk"
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, _)) =>
    Seq(
      "-Ycache-plugin-class-loader:last-modified",
      "-Ycache-macro-class-loader:last-modified",
      "-Ywarn-dead-code",
      "-Xlint",
      "-Yrangepos",
      "-Xsource:3",
      "-Xmaxwarns",
      200.toString,
      "-Wconf:cat=lint-multiarg-infix:silent",
      "-Xlint:-byname-implicit",
      "-Ymacro-annotations"
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

publish / skip := true

val pekkoVersion = "1.0.1"

val scalatestVersion = "3.2.17"

val `ch.qos.logback_logback-classic`           = "ch.qos.logback"              % "logback-classic" % "1.2.12"
val `com.typesafe.akka_akka-slf4j`             = "org.apache.pekko"           %% "pekko-slf4j"     % pekkoVersion
val `com.typesafe.akka_stream`                 = "org.apache.pekko"           %% "pekko-stream"    % pekkoVersion
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
  .dependsOn(`pekko-smtp`)
  .dependsOn(Seq(`smtp-util`, `pekko-smtp`).map(_ % "test->test")*)

lazy val `pekko-smtp` = projectName("pekko-smtp", file("pekko-smtp"))
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
