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

val springBootAdminVersion = "2.4.1"
val springCloudSleuthVersion = "3.0.3"
val springCloudAwsVersion = "2.2.5.RELEASE"

plugins {
    java
    groovy
    id("org.springframework.boot") version "2.5.1"
    id("com.gorylenko.gradle-git-properties") version "2.3.1"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("com.diffplug.spotless") version "5.12.5"
    id("io.freefair.lombok") version "6.0.0-m2"
    jacoco
}

spotless {
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        googleJavaFormat()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

gitProperties {
    extProperty = "gitProps" // git properties will be put in a map at project.ext.gitProps
}

apply(from = "./gradle/packaging.gradle.kts")

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

repositories {
    mavenCentral()
    maven("https://raw.githubusercontent.com/TulevaEE/releases-repo/master/")
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
    implementation("org.springframework.cloud:spring-cloud-starter-sleuth")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("org.springframework.session:spring-session-jdbc")

    implementation("de.codecentric:spring-boot-admin-starter-client")
    implementation("org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.5.0")
    implementation("org.springframework.security.oauth:spring-security-oauth2:2.5.1.RELEASE")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.jsoup:jsoup:1.13.1")
    implementation("commons-net:commons-net:3.8.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("net.sf.ehcache:ehcache")
    implementation("org.decampo:xirr:1.1")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.68")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:3.0.1")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")

    implementation("com.github.SK-EID:smart-id-java-client:1.6.1")
    // TODO: upgrade
    // implementation("ee.sk.smartid:smart-id-java-client:2.0")
    implementation("org.digidoc4j:digidoc4j:4.2.0") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    implementation("io.sentry:sentry-spring-boot-starter:5.1.2")
    implementation("io.sentry:sentry-logback:5.2.0")

    implementation("com.vladmihalcea:hibernate-types-52:2.11.1")

    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("ee.sk.mid:mid-rest-java-client:1.3")

    implementation("com.google.guava:guava:30.1.1-jre")

    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "spock-core")
    }

    testImplementation("org.spockframework:spock-core:2.0-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.spockframework:spock-spring:2.0-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.codehaus.groovy:groovy:3.0.8")
    testImplementation("org.mock-server:mockserver-netty:5.11.2")
    testImplementation("org.mock-server:mockserver-spring-test-listener:5.11.2")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockftpserver:MockFtpServer:2.8.0")
    testImplementation("io.github.origin-energy:java-snapshot-testing-spock:3.2.5")
    testImplementation("io.github.origin-energy:java-snapshot-testing-plugin-jackson:3.2.5")
}

dependencyManagement {
    imports {
        mavenBom("de.codecentric:spring-boot-admin-dependencies:$springBootAdminVersion")
        mavenBom("org.springframework.cloud:spring-cloud-sleuth:$springCloudSleuthVersion")
    }
}

tasks {
    test {
        testLogging {
            events = setOf(
                STARTED,
                PASSED,
                FAILED,
                SKIPPED,
                STANDARD_OUT,
                STANDARD_ERROR
            )
            showCauses = true
            showExceptions = true
            showStackTraces = true
            exceptionFormat = FULL
        }
        useJUnitPlatform()
    }

    bootRun {
        systemProperty("file.encoding", "utf-8")
        systemProperty("spring.profiles.active", "dev")
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.isEnabled = true
            html.isEnabled = false
        }
    }

    check {
        dependsOn(jacocoTestReport)
    }
}
