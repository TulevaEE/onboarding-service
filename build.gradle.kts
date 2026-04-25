import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class ExecTask
    @Inject
    constructor(
        @Internal val execOps: ExecOperations,
    ) : DefaultTask()

val xjc by configurations.creating

buildscript {
    repositories {
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}

val springCloudVersion = "2025.1.1"
val springModulithVersion = "2.0.5"

plugins {
    java
    groovy
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.gorylenko.gradle-git-properties") version "2.5.7"
    id("com.diffplug.spotless") version "8.4.0"
    id("io.freefair.lombok") version "9.2.0"
    jacoco
}

lombok {
    version = "1.18.42"
}

spotless {
    java {
        target("src/*/java/**/*.java", "src/*/groovy/**/*.java")
        removeUnusedImports()
        googleJavaFormat("1.32.0")
        replaceRegex("Remove String Templates", "STR\\.\"\"\"", "\"\"\"")
        replaceRegex("Remove String Templates interpolation", "\\\\\\{([^}]*)\\}", "%s")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
    groovy {
        target("src/*/groovy/**/*.groovy")
        removeSemicolons()
    }
}

gitProperties {
    extProperty = "gitProps" // git properties will be put in a map at project.ext.gitProps
}

apply(from = "./gradle/packaging.gradle.kts")

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://gitlab.com/api/v4/projects/19948337/packages/maven")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.jspecify:jspecify:1.0.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")

    implementation("com.nimbusds:nimbus-jose-jwt:10.9")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.springdoc:springdoc-openapi-starter-common:3.0.3")
    implementation("org.springframework.session:spring-session-jdbc")

    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("commons-net:commons-net:3.13.0")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.decampo:xirr:1.2")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:5.0.0")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.5")
    implementation("org.springframework.boot:spring-boot-starter-web-services")
    testImplementation("org.springframework.ws:spring-ws-test")

    xjc("org.glassfish.jaxb:jaxb-xjc:4.0.7")
    xjc("org.glassfish.jaxb:jaxb-runtime:4.0.7")
    xjc("org.glassfish.jaxb:jaxb-core:4.0.7")
    xjc("org.glassfish.jaxb:codemodel:4.0.7")
    xjc("org.glassfish.jaxb:xsom:4.0.7")
    xjc("io.github.threeten-jaxb:threeten-jaxb-core:2.2.0")

    implementation("io.github.threeten-jaxb:threeten-jaxb-core:2.2.0")

    implementation("ee.sk.smartid:smart-id-java-client:3.2") {
        exclude(group = "org.bouncycastle")
    }
    implementation("ee.sk.mid:mid-rest-java-client:1.6") {
        exclude(group = "org.bouncycastle")
    }
    implementation("eu.webeid.security:authtoken-validation:3.2.0")

    implementation("org.digidoc4j:digidoc4j:6.1.0") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.bouncycastle:bcpkix-jdk18on:1.84")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bouncycastle:bcutil-jdk18on:1.84")
    implementation("org.apache.httpcomponents.client5:httpclient5")

    implementation("io.sentry:sentry-spring-boot-4:8.39.1")
    implementation("io.sentry:sentry-logback:8.39.1")

    // TODO: replace with mailchimp-transactional-api-java
    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("com.github.ErkoRisthein:mailchimp-transactional-api-java:bc3af49c1a")
    implementation("com.github.ErkoRisthein:mailchimp-marketing-api-java:3.0.90-fix3")

    implementation("jakarta.xml.bind:jakarta.xml.bind-api")

    implementation("software.amazon.awssdk:s3:2.42.36")
    implementation("commons-io:commons-io:2.21.0")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0")

    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "spock-core")
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.spockframework:spock-core:2.4-groovy-5.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.spockframework:spock-spring:2.4-groovy-5.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.apache.groovy:groovy:5.0.5")
    testImplementation("org.apache.groovy:groovy-json:5.0.5")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:jdbc:1.21.4")

    // TODO: migrate to WireMock
    testImplementation("org.mock-server:mockserver-netty:5.15.0")
    testImplementation("org.mock-server:mockserver-spring-test-listener:5.15.0")

    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockftpserver:MockFtpServer:3.2.0")
    testImplementation("io.github.origin-energy:java-snapshot-testing-spock:4.0.8")
    testImplementation("io.github.origin-energy:java-snapshot-testing-plugin-jackson:4.0.8")
    testImplementation("io.github.origin-energy:java-snapshot-testing-junit5:4.0.8")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        mavenBom("org.springframework.modulith:spring-modulith-bom:$springModulithVersion")
    }
}

tasks {
    test {
        testLogging {
            events =
                setOf(
                    FAILED,
                    SKIPPED,
                    STANDARD_OUT,
                    STANDARD_ERROR,
                )
            showCauses = true
            showExceptions = true
            showStackTraces = true
            exceptionFormat = FULL
        }
        useJUnitPlatform()
        shouldRunAfter(spotlessCheck)

        // Enable parallel test execution for faster builds
        // CI fork count is set via -DmaxParallelForks in .circleci/config.yml
        maxParallelForks =
            (System.getProperty("maxParallelForks")?.toIntOrNull())
                ?: if (System.getenv("CI") == "true") {
                    2 // default, overridden by -DmaxParallelForks in CI
                } else {
                    3 // fewer forks = better Spring context cache reuse
                }
    }

    bootRun {
        systemProperty("file.encoding", "utf-8")
        systemProperty("spring.profiles.active", "dev")
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    check {
        dependsOn(jacocoTestReport)
    }

    jacocoTestCoverageVerification {
        dependsOn(test)
        violationRules {
            rule {
                element = "PACKAGE"
                includes =
                    listOf("ee.tuleva.onboarding.aml", "ee.tuleva.onboarding.aml.*", "ee.tuleva.onboarding.aml.**")

                limit {
                    counter = "CLASS"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "METHOD"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "0.9".toBigDecimal()
                }

                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }
            }
            rule {
                element = "PACKAGE"
                includes =
                    listOf(
                        "ee.tuleva.onboarding.deadline",
                        "ee.tuleva.onboarding.deadline.*",
                        "ee.tuleva.onboarding.deadline.**",
                    )

                limit {
                    counter = "CLASS"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "METHOD"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }
            }
        }
    }

    register<ExecTask>("setupGitHooks") {
        group = "git hooks"
        description = "Configures git hooks for the project"

        doLast {
            val operatingSystem =
                org.gradle.internal.os.OperatingSystem
                    .current()
            val shellCommand = if (operatingSystem.isWindows) "cmd" else "sh"
            val shellArg = if (operatingSystem.isWindows) "/c" else "-c"

            execOps.exec {
                commandLine(shellCommand, shellArg, "git config core.hooksPath .githooks")
            }
            execOps.exec {
                commandLine(shellCommand, shellArg, "chmod +x .githooks/pre-commit")
            }
            println("Git hooks configured successfully!")
        }
    }

    register<ExecTask>("generateXSDClasses") {
        group = "code generation"
        description = "Generates Java classes from XSD files"

        val xjcClasspath = configurations["xjc"].asPath

        val iso20022Dir = file("$projectDir/src/main/resources/banking/iso20022")
        val iso20022OutputDir = file("${layout.buildDirectory.get()}/generated-sources/iso20022")
        val iso20022Schemas =
            listOf(
                file("$iso20022Dir/camt.060.001.03.xsd") to "ee.tuleva.onboarding.banking.iso20022.camt060",
                file("$iso20022Dir/camt.052.001.02.xsd") to "ee.tuleva.onboarding.banking.iso20022.camt052",
                file("$iso20022Dir/camt.053.001.02.xsd") to "ee.tuleva.onboarding.banking.iso20022.camt053",
            )

        val ariregisterDir = file("$projectDir/src/main/resources/ariregister")
        val ariregisterOutputDir = file("${layout.buildDirectory.get()}/generated-sources/ariregister")
        val ariregisterSchemas =
            listOf(
                file("$ariregisterDir/ettevottegaSeotudIsikud_v1.xsd") to "ee.tuleva.onboarding.ariregister.generated",
                file(
                    "$ariregisterDir/detailandmed_v2.xsd",
                ) to "ee.tuleva.onboarding.ariregister.generated.detailandmed",
            )

        val bindingsFile = file("$projectDir/src/main/resources/jaxb-bindings.xjb")

        inputs.dir(iso20022Dir)
        inputs.dir(ariregisterDir)
        inputs.file(bindingsFile)
        outputs.dir(iso20022OutputDir)
        outputs.dir(ariregisterOutputDir)

        doLast {
            iso20022OutputDir.mkdirs()
            iso20022Schemas.forEach { (schemaFile, packageName) ->
                execOps.exec {
                    executable = "java"
                    args(
                        "-cp",
                        xjcClasspath,
                        "com.sun.tools.xjc.XJCFacade",
                        "-extension",
                        "-d",
                        iso20022OutputDir.absolutePath,
                        "-p",
                        packageName,
                        "-b",
                        bindingsFile.absolutePath,
                        schemaFile.absolutePath,
                    )
                }
            }

            ariregisterOutputDir.mkdirs()
            ariregisterSchemas.forEach { (schemaFile, packageName) ->
                execOps.exec {
                    executable = "java"
                    args(
                        "-Xss4m",
                        "-cp",
                        xjcClasspath,
                        "com.sun.tools.xjc.XJCFacade",
                        "-nv",
                        "-extension",
                        "-d",
                        ariregisterOutputDir.absolutePath,
                        "-p",
                        packageName,
                        "-b",
                        bindingsFile.absolutePath,
                        schemaFile.absolutePath,
                    )
                }
            }
        }
    }

    build {
        dependsOn("generateXSDClasses")
        dependsOn("setupGitHooks")
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named("generateXSDClasses"))
}

sourceSets {
    main {
        java {
            srcDir("${layout.buildDirectory.get()}/generated-sources/iso20022")
            srcDir("${layout.buildDirectory.get()}/generated-sources/ariregister")
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Xlint:-processing")
    options.compilerArgs.add("-Xlint:-path")
    options.compilerArgs.add("-Xlint:-serial")
    options.compilerArgs.add("-Xlint:-deprecation")
    options.compilerArgs.add("-Xdiags:verbose")
//    options.compilerArgs.add("-Werror")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

tasks.withType<Test> {
    jvmArgs(
        "--enable-preview",
        "-XX:+UseParallelGC",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=/tmp/heapdump.hprof",
    )
    // CircleCI Large (Docker): 8GB RAM, 2 forks × 2GB = 4GB, leaves 4GB for OS/Gradle/PostgreSQL
    // Local dev: 16GB RAM, 3 forks × 2GB = 6GB, leaves 10GB for OS/IDE
    maxHeapSize = "2g"
}
