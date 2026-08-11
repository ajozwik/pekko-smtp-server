resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"       % "0.14.7")
addSbtPlugin("com.github.sbt"    % "sbt-pgp"            % "2.3.1")
addSbtPlugin("com.github.sbt"    % "sbt-license-report" % "1.9.0")
addSbtPlugin("com.indoorvivants" % "sbt-revolver"       % "0.11.2")
addSbtPlugin("org.scalameta"     % "sbt-mdoc"           % "2.9.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.6.1")
// addSbtPlugin("org.scoverage"   % "sbt-coveralls"      % "1.4.0")
addSbtPlugin("org.scoverage"   % "sbt-scoverage"   % "2.4.4")
addSbtPlugin("org.xerial.sbt"  % "sbt-pack"        % "1.0.0")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "3.6.1")

libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
