ThisBuild / developers := List(
  Developer(
    id    = "ajozwik",
    name  = "Andrzej Jozwik",
    email = "andrzej.jozwik@gmail.com",
    url   = url("https://github.com/ajozwik")
  )
)

ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) {
    Option("snapshots" at nexus + "content/repositories/snapshots")
  }
  else {
    Option("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

ThisBuild / publishMavenStyle := true

Test / publishArtifact := false

ThisBuild / pomIncludeRepository := { _ => false }

ThisBuild / licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))

val organizationUrl = "https://github.com/ajozwik"

val projectUrl = s"$organizationUrl/akka-smtp-server"

ThisBuild / scmInfo := Option(
  ScmInfo(
    url(projectUrl),
    "scm:git@github.com:ajozwik/akka-smtp-server.git"
  )
)

ThisBuild / organizationHomepage := Option(url(organizationUrl))

ThisBuild / homepage := Option(url(projectUrl))