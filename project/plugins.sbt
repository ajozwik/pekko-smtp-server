resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.codacy"              % "sbt-codacy-coverage"   % "1.3.15")
addSbtPlugin("com.github.sbt"          % "sbt-pgp"               % "2.1.2")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat"         % "1.1.0")
addSbtPlugin("com.typesafe.sbt"        % "sbt-license-report"    % "1.2.0")
addSbtPlugin("io.spray"                % "sbt-revolver"          % "0.9.1")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt"          % "2.4.2")
addSbtPlugin("org.scalastyle"         %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage"           % "sbt-coveralls"         % "1.3.1")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"         % "1.6.1")
addSbtPlugin("org.xerial.sbt"          % "sbt-pack"              % "0.13")
addSbtPlugin("org.xerial.sbt"          % "sbt-sonatype"          % "3.9.7")
