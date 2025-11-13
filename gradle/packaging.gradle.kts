tasks {
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
        dependsOn(unpack)
    }
}
