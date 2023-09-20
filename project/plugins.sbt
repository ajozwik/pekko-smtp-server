resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("ch.epfl.scala"           % "sbt-scalafix"        % "0.11.1")
addSbtPlugin("com.codacy"              % "sbt-codacy-coverage" % "3.0.3")
addSbtPlugin("com.github.sbt"          % "sbt-pgp"             % "2.2.1")
addSbtPlugin("com.typesafe.sbt"        % "sbt-license-report"  % "1.2.0")
addSbtPlugin("io.spray"                % "sbt-revolver"        % "0.10.0")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt"        % "2.4.6")
addSbtPlugin("org.scoverage"           % "sbt-coveralls"       % "1.3.9")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"       % "2.0.9")
addSbtPlugin("org.xerial.sbt"          % "sbt-pack"            % "0.17")
addSbtPlugin("org.xerial.sbt"          % "sbt-sonatype"        % "3.9.21")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
