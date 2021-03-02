val gitProps: Map<String, String> by extra

tasks {
  val collectFiles by creating(Copy::class) {
    dependsOn("jar", "generateGitProperties")
    from("$rootDir/etc/eb/.ebextensions") {
      into(".ebextensions")
    }
    from("$rootDir/etc/eb/Procfile")
    val jar = tasks.getByName<Jar>("jar")
    from(jar.outputs.files.singleFile) {
      rename { "application.jar" }
    }
    destinationDir = file("$buildDir/zip")
    doLast {
      copy {
        from("$rootDir/etc/eb/docker-compose.yml") {
          expand("hash" to gitProps["git.commit.id"]?.substring(0, 8))
        }
        into("$buildDir/zip")
      }
    }
  }

  val zipWithExtensions by creating(Zip::class) {
    dependsOn(collectFiles)
    from(collectFiles)
  }

  val unpack by creating(Copy::class) {
    dependsOn(named("bootJar"))
    from(zipTree(named("bootJar").get().outputs.files.singleFile)) {
      into("dependency")
    }
    from("$rootDir/etc/docker/entrypoint.sh") {
      into("dependency")
    }
    from("$rootDir/etc/docker/Dockerfile")
    into("build/docker")
  }

  named("assemble") {
    dependsOn(unpack, zipWithExtensions)
  }
}
