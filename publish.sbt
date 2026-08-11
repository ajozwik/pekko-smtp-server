val organizationUrl = "https://github.com/ajozwik"
val projectUrl      = s"$organizationUrl/pekko-smtp-server"

organizationHomepage := Option(uri(organizationUrl))

scmInfo := Option(
  ScmInfo(
    uri(projectUrl),
    "scm:git@github.com:ajozwik/pekko-smtp-server.git"
  )
)

developers := List(
  Developer(
    id = "ajozwik",
    name = "Andrzej Jozwik",
    email = "andrzej.jozwik@gmail.com",
    url = uri(organizationUrl)
  )
)

description          := "Smtp server based on pekko stream."
licenses             := Seq("MIT License" -> uri("https://www.opensource.org/licenses/mit-license.php"))
homepage             := Option(uri(projectUrl))
pomIncludeRepository := { _ => false }
publishMavenStyle    := true

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Option("central-snapshots" at centralSnapshots)
  else localStaging.value
}

Test / publishArtifact := false

versionScheme := Option("semver-spec")
