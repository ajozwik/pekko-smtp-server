resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.codacy"              % "sbt-codacy-coverage" % "1.3.15")
addSbtPlugin("com.github.sbt"          % "sbt-pgp"             % "2.2.1")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat"       % "1.1.1")
addSbtPlugin("com.typesafe.sbt"        % "sbt-license-report"  % "1.2.0")
addSbtPlugin("io.spray"                % "sbt-revolver"        % "0.9.1")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt"        % "2.4.6")
addSbtPlugin("org.scoverage"           % "sbt-coveralls"       % "1.3.5")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"       % "2.0.5")
addSbtPlugin("org.xerial.sbt"          % "sbt-pack"            % "0.17")
addSbtPlugin("org.xerial.sbt"          % "sbt-sonatype"        % "3.9.15")
addSbtPlugin("org.wartremover"         % "sbt-wartremover"     % "3.0.9")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
