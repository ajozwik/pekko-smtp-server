import sbt.Keys.localStaging
import sbt.url

val organizationUrl = "https://github.com/ajozwik"
val projectUrl      = s"$organizationUrl/pekko-smtp-server"

ThisBuild / organizationHomepage := Option(url(organizationUrl))

ThisBuild / scmInfo := Option(
  ScmInfo(
    url(projectUrl),
    "scm:git@github.com:ajozwik/pekko-smtp-server.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "ajozwik",
    name = "Andrzej Jozwik",
    email = "andrzej.jozwik@gmail.com",
    url = url(organizationUrl)
  )
)

ThisBuild / description          := "Smtp server based on pekko stream."
ThisBuild / licenses             := Seq("MIT License" -> url("https://www.opensource.org/licenses/mit-license.php"))
ThisBuild / homepage             := Option(url(projectUrl))
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle    := true

ThisBuild / publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Option("central-snapshots" at centralSnapshots)
  else localStaging.value
}

Test / publishArtifact := false

ThisBuild / versionScheme := Option("semver-spec")
