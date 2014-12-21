crossScalaVersions := Common.CrossScalaVersions

publishLocal := {}
publish := {}
publishArtifact := false

lazy val akkaPersistenceMongoCommon = project in file(Common.NameCommon)

lazy val akkaPersistenceMongoCasbah = (
  project in file(Common.NameCasbah)
    dependsOn(akkaPersistenceMongoCommon % Common.TestCompile)
)
