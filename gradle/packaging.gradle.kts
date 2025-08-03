val gitProps: Map<String, String> by extra

tasks {
    val collectFiles by registering(Copy::class) {
        dependsOn("jar", "generateGitProperties")

        from("$rootDir/etc/eb/.ebextensions") { into(".ebextensions") }
        from("$rootDir/etc/eb/.platform") { into(".platform") }

        into(layout.buildDirectory.dir("zip"))

        doLast {
            copy {
                from("$rootDir/etc/eb/docker-compose.yml") {
                    expand(mapOf("hash" to gitProps["git.commit.id"]))
                }
                into(layout.buildDirectory.dir("zip"))
            }
        }
    }

    val zipWithExtensions by registering(Zip::class) {
        dependsOn(collectFiles)
        from(collectFiles)
    }

    val unpack by registering(Copy::class) {
        dependsOn(named("bootJar"))

        from(zipTree(named("bootJar").get().outputs.files.singleFile)) { into("dependency") }
        from("$rootDir/etc/docker/entrypoint.sh") { into("dependency") }
        from("$rootDir/etc/docker/Dockerfile")
        from("$rootDir/etc/docker/rds-ca-2019-root.pem")
        from("$rootDir/etc/docker/rds-ca-2024-root.pem")

        into(layout.buildDirectory.dir("docker"))
    }

    named("assemble") {
        dependsOn(unpack, zipWithExtensions)
    }
}
