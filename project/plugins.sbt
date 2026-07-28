resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("ch.epfl.scala"   % "sbt-scalafix"       % "0.14.7")
addSbtPlugin("com.github.sbt"  % "sbt-pgp"            % "2.3.1")
addSbtPlugin("com.github.sbt"  % "sbt-license-report" % "1.9.0")
addSbtPlugin("io.spray"        % "sbt-revolver"       % "0.10.0")
addSbtPlugin("org.scalameta"   % "sbt-mdoc"           % "2.9.0")
addSbtPlugin("org.scalameta"   % "sbt-scalafmt"       % "2.6.1")
addSbtPlugin("org.scoverage"   % "sbt-coveralls"      % "1.3.15")
addSbtPlugin("org.scoverage"   % "sbt-scoverage"      % "2.4.4")
addSbtPlugin("org.xerial.sbt"  % "sbt-pack"           % "0.23")
addSbtPlugin("org.wartremover" % "sbt-wartremover"    % "3.6.1")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
