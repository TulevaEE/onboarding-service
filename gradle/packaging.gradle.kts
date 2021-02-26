tasks {

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
        dependsOn(unpack)
    }
}
