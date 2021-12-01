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

val springBootAdminVersion = "2.5.4"
val springCloudSleuthVersion = "3.0.4"
val springCloudAwsVersion = "2.2.5.RELEASE"

plugins {
    java
    groovy
    id("org.springframework.boot") version "2.5.6"
    id("com.gorylenko.gradle-git-properties") version "2.3.1"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("com.diffplug.spotless") version "5.17.1"
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
    implementation("org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.5.5")
    implementation("org.springframework.security.oauth:spring-security-oauth2:2.5.1.RELEASE")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("commons-net:commons-net:3.8.0")
    implementation("org.apache.commons:commons-lang3")
    implementation("net.sf.ehcache:ehcache:2.10.9.2")
    implementation("org.decampo:xirr:1.1")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:3.0.2")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")

    implementation("ee.sk.smartid:smart-id-java-client:2.1.2")
    implementation("org.digidoc4j:digidoc4j:4.2.1") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    implementation("io.sentry:sentry-spring-boot-starter:5.3.0")
    implementation("io.sentry:sentry-logback:5.3.0")

    implementation("com.vladmihalcea:hibernate-types-52:2.14.0")

    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("ee.sk.mid:mid-rest-java-client:1.3")

    implementation("com.google.guava:guava:31.0.1-jre")

    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")

    testCompileOnly("org.projectlombok:lombok:1.18.22")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.22")

    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "spock-core")
        exclude(module = "junit-vintage-engine")
    }

    testImplementation("org.spockframework:spock-core:2.0-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.spockframework:spock-spring:2.0-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.codehaus.groovy:groovy:3.0.9")
    testImplementation("org.mock-server:mockserver-netty:5.11.2")
    testImplementation("org.mock-server:mockserver-spring-test-listener:5.11.2")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockftpserver:MockFtpServer:3.0.0")
    testImplementation("io.github.origin-energy:java-snapshot-testing-spock:3.2.6")
    testImplementation("io.github.origin-energy:java-snapshot-testing-plugin-jackson:3.2.6")
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
