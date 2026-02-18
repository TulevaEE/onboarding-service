import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.process.ExecOperations

val execOps = project.serviceOf<ExecOperations>()
val xjc by configurations.creating

buildscript {
    repositories {
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}

val springCloudVersion = "2025.0.1"
val springModulithVersion = "1.4.6"

plugins {
    java
    groovy
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.gorylenko.gradle-git-properties") version "2.5.4"
    id("com.diffplug.spotless") version "8.1.0"
    id("io.freefair.lombok") version "9.1.0"
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
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.jspecify:jspecify:1.0.0")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    implementation("com.nimbusds:nimbus-jose-jwt:10.6")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15")
    implementation("org.springdoc:springdoc-openapi-starter-common:2.8.15")
    implementation("org.springframework.session:spring-session-jdbc")

    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("commons-net:commons-net:3.12.0")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.decampo:xirr:1.2")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:4.0.9")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")

    xjc("org.glassfish.jaxb:jaxb-xjc:4.0.5")

    implementation("ee.sk.smartid:smart-id-java-client:2.3.1") {
        exclude(group = "org.bouncycastle")
    }
    implementation("ee.sk.mid:mid-rest-java-client:1.6") {
        exclude(group = "org.bouncycastle")
    }
    implementation("eu.webeid.security:authtoken-validation:3.2.0")

    implementation("org.digidoc4j:digidoc4j:6.1.0") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.apache.httpcomponents.client5:httpclient5")

    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.29.0")
    implementation("io.sentry:sentry-logback:8.29.0")

    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.14.1")

    // TODO: replace with mailchimp-transactional-api-java
    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("com.github.ErkoRisthein:mailchimp-transactional-api-java:1.0.59")
    implementation("com.github.ErkoRisthein:mailchimp-marketing-api-java:3.0.90-fix3")

    implementation("jakarta.xml.bind:jakarta.xml.bind-api")

    implementation("software.amazon.awssdk:s3:2.41.1")
    implementation("commons-io:commons-io:2.21.0")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    implementation("net.javacrumbs.shedlock:shedlock-spring:7.5.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.5.0")

    implementation("org.springframework.modulith:spring-modulith-starter-core")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")

    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "spock-core")
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.spockframework:spock-core:2.4-groovy-5.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.spockframework:spock-spring:2.4-groovy-5.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.apache.groovy:groovy:5.0.3")
    testImplementation("org.apache.groovy:groovy-json:5.0.3")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:jdbc")

    // TODO: migrate to WireMock
    testImplementation("org.mock-server:mockserver-netty:5.15.0")
    testImplementation("org.mock-server:mockserver-spring-test-listener:5.15.0")

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
                    STARTED,
                    PASSED,
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
        // CircleCI Large has 4 vCPUs, so use all 4 cores
        maxParallelForks =
            if (System.getenv("CI") == "true") {
                4 // CircleCI Large: 4 vCPUs
            } else {
                (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1) // Use half of available cores locally
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

    register("setupGitHooks") {
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

    register("generateXSDClasses") {
        group = "code generation"
        description = "Generates Java classes from XSD files"

        val xsdDir = file("$projectDir/src/main/resources/banking/iso20022")
        val outputDir = file("${layout.buildDirectory.get()}/generated-sources/iso20022")
        val rootSchemas =
            listOf(
                file("$xsdDir/camt.060.001.03.xsd") to "ee.tuleva.onboarding.banking.iso20022.camt060",
                file("$xsdDir/camt.052.001.02.xsd") to "ee.tuleva.onboarding.banking.iso20022.camt052",
                file("$xsdDir/camt.053.001.02.xsd") to "ee.tuleva.onboarding.banking.iso20022.camt053",
            )

        doLast {
            outputDir.mkdirs()

            rootSchemas.forEach { (schemaFile, packageName) ->
                execOps.exec {
                    executable = "java"
                    args(
                        "-cp",
                        configurations["xjc"].asPath,
                        "com.sun.tools.xjc.XJCFacade",
                        "-d",
                        outputDir.absolutePath,
                        "-p",
                        packageName,
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
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=/tmp/heapdump.hprof",
    )
    // CircleCI Large: 15GB RAM, 4 parallel forks = 3GB per fork (12GB for tests, 3GB for OS/container/Gradle)
    // Local dev: Assume 16GB+ RAM (most devs have 16-32GB)
    maxHeapSize =
        if (System.getenv("CI") == "true") {
            "3g" // CircleCI Large has 15GB RAM
        } else {
            "4g" // Generous for local dev (16GB+ RAM)
        }
}
