import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED

buildscript {
    repositories {
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}

val springCloudVersion = "2024.0.0"

plugins {
    java
    groovy
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
    id("com.diffplug.spotless") version "6.25.0"
    id("io.freefair.lombok") version "8.11"
    jacoco
}

lombok {
    version = "1.18.34"
}

spotless {
    java {
        target("src/*/java/**/*.java", "src/*/groovy/**/*.java")
        removeUnusedImports()
        googleJavaFormat()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
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
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")
    implementation("org.springdoc:springdoc-openapi-starter-common:2.8.3")
    implementation("org.springframework.session:spring-session-jdbc")

    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("commons-net:commons-net:3.11.1")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.decampo:xirr:1.2")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:4.0.5")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")

    implementation("ee.sk.smartid:smart-id-java-client:2.3") {
        exclude(group = "org.bouncycastle")
    }
    implementation("ee.sk.mid:mid-rest-java-client:1.5") {
        exclude(group = "org.bouncycastle")
    }

    implementation("org.digidoc4j:digidoc4j:5.3.1") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.apache.httpcomponents.client5:httpclient5")

    implementation("io.sentry:sentry-spring-boot-starter-jakarta:7.20.0")
    implementation("io.sentry:sentry-logback:7.20.0")

    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.0")

    // TODO: replace with mailchimp-transactional-api-java
    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("com.github.ErkoRisthein:mailchimp-transactional-api-java:1.0.59")
    implementation("com.github.ErkoRisthein:mailchimp-marketing-api-java:3.0.55")

    implementation("jakarta.xml.bind:jakarta.xml.bind-api")

    implementation("software.amazon.awssdk:s3:2.29.44")
    implementation("commons-io:commons-io:2.18.0")
    implementation("org.apache.commons:commons-csv:1.12.0")

    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "spock-core")
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.spockframework:spock-spring:2.4-M4-groovy-4.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.apache.groovy:groovy-all:4.0.24")
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
                includes = listOf("ee.tuleva.onboarding.aml.*")

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
                    minimum = "0.97".toBigDecimal()
                }

                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "1.0".toBigDecimal()
                }

                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = "0.97".toBigDecimal()
                }
            }
        }
    }

    register("setupGitHooks") {
        group = "git hooks"
        description = "Configures git hooks for the project"

        doLast {
            val operatingSystem = org.gradle.internal.os.OperatingSystem.current()
            val shellCommand = if (operatingSystem.isWindows) "cmd" else "sh"
            val shellArg = if (operatingSystem.isWindows) "/c" else "-c"

            exec {
                commandLine(shellCommand, shellArg, "git config core.hooksPath .githooks")
            }
            exec {
                commandLine(shellCommand, shellArg, "chmod +x .githooks/pre-commit")
            }
            println("Git hooks configured successfully!")
        }
    }

    build {
        dependsOn("setupGitHooks")
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

tasks.withType<Test> {
    jvmArgs("--enable-preview")
}
