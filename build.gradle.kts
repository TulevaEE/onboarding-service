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

val springBootAdminVersion = "2.5.2"
val springCloudSleuthVersion = "3.0.4"
val springCloudAwsVersion = "2.2.5.RELEASE"

plugins {
    java
    groovy
    id("org.springframework.boot") version "2.5.6"
    id("com.gorylenko.gradle-git-properties") version "2.3.1"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("com.diffplug.spotless") version "5.17.0"
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
    implementation("org.springframework.boot:spring-boot-starter-actuator:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-security:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-web:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-cache:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-aop:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-json:2.4.10")
    implementation("org.springframework.boot:spring-boot-starter-validation:2.5.6")
    implementation("org.springframework.cloud:spring-cloud-starter-sleuth:3.0.4")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("org.springframework.session:spring-session-jdbc:2.4.5")

    implementation("de.codecentric:spring-boot-admin-starter-client:2.5.2")
    implementation("org.springframework.security.oauth.boot:spring-security-oauth2-autoconfigure:2.5.5")
    implementation("org.springframework.security.oauth:spring-security-oauth2:2.5.1.RELEASE")

    developmentOnly("org.springframework.boot:spring-boot-devtools:2.5.6")
    runtimeOnly("org.postgresql:postgresql:42.2.23")

    implementation("org.flywaydb:flyway-core:7.1.1")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:2.4.10")
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("commons-net:commons-net:3.8.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("net.sf.ehcache:ehcache:2.10.9.2")
    implementation("org.decampo:xirr:1.1")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:3.0.2")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:3.0.1")

    implementation("ee.sk.smartid:smart-id-java-client:2.1.1")
    implementation("org.digidoc4j:digidoc4j:4.2.1") {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    implementation("io.sentry:sentry-spring-boot-starter:5.2.4")
    implementation("io.sentry:sentry-logback:5.2.4")

    implementation("com.vladmihalcea:hibernate-types-52:2.13.0")

    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("ee.sk.mid:mid-rest-java-client:1.3")

    implementation("com.google.guava:guava:31.0.1-jre")

    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")

    testCompileOnly("org.projectlombok:lombok:1.18.22")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.22")

    testImplementation("com.h2database:h2:1.4.200")
    testImplementation("org.springframework.boot:spring-boot-starter-test:2.5.6") {
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
    testImplementation("org.springframework.security:spring-security-test:5.5.3")
    testImplementation("org.mockftpserver:MockFtpServer:3.0.0")
    testImplementation("com.github.TulevaEE.java-snapshot-testing:java-snapshot-testing-spock:-SNAPSHOT")
    testImplementation("com.github.TulevaEE.java-snapshot-testing:java-snapshot-testing-plugin-jackson:-SNAPSHOT")
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
