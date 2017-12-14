resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.2")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.0")

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.9.0")

addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.11")

libraryDependencies += "org.scalariform" %% "scalariform" % "0.1.8"


addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "1.2.0")
