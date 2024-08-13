val gitProps: Map<String, String> by extra

tasks {
  val collectFiles by creating(Copy::class) {
    dependsOn("jar", "generateGitProperties")
    from("$rootDir/etc/eb/.ebextensions") {
      into(".ebextensions")
    }
    from("$rootDir/etc/eb/.platform") {
      into(".platform")
    }
    destinationDir = file("$buildDir/zip")
    doLast {
      copy {
        from("$rootDir/etc/eb/docker-compose.yml") {
          expand("hash" to gitProps["git.commit.id"])
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
    from("$rootDir/etc/docker/rds-ca-eu-central-1-bundle-2024.pem")
    into("build/docker")
  }

  named("assemble") {
    dependsOn(unpack, zipWithExtensions)
  }
}
