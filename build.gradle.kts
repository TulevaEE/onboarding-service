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

val springCloudVersion = "2021.0.9"

plugins {
    java
    groovy
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.1.4"
    id("com.gorylenko.gradle-git-properties") version "2.4.1"
    id("com.diffplug.spotless") version "6.25.0"
    jacoco
}

spotless {
    java {
        target("src/*/java/**/*.java", "src/*/groovy/**/*.java")
        removeUnusedImports()
        googleJavaFormat()
    }
    kotlinGradle {
        target("*.gradle.kts")
        //ktlint() // disable for now, can try if it works after upgrading spring boot to v3+
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

    implementation("org.springframework.cloud:spring-cloud-starter-sleuth")
    implementation("org.springframework.session:spring-session-jdbc")

    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    implementation("org.springdoc:springdoc-openapi-ui:1.7.0")
    implementation("org.springdoc:springdoc-openapi-security:1.7.0")

    runtimeOnly("org.postgresql:postgresql")

    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("commons-net:commons-net:3.10.0")
    implementation("org.apache.commons:commons-lang3")
    implementation("net.sf.ehcache:ehcache:2.10.9.2")
    implementation("org.decampo:xirr:1.2")
    implementation("org.eclipse.persistence:org.eclipse.persistence.moxy:4.0.2")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")

    implementation("ee.sk.smartid:smart-id-java-client:2.1.4") {
        exclude(group = "org.bouncycastle")
    }
    implementation("ee.sk.mid:mid-rest-java-client:1.4") {
        exclude(group = "org.bouncycastle")
    }
    implementation("org.digidoc4j:digidoc4j:5.2.0") {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "org.bouncycastle")
    }
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.76")

    implementation("io.sentry:sentry-spring-boot-starter:7.5.0")
    implementation("io.sentry:sentry-logback:7.5.0")

    implementation("com.vladmihalcea:hibernate-types-55:2.21.1")

    implementation("com.mandrillapp.wrapper.lutung:lutung:0.0.8")

    implementation("com.github.ErkoRisthein:mailchimp-transactional-api-java:master-SNAPSHOT")
    implementation("com.github.ErkoRisthein:mailchimp-marketing-api-java:master-SNAPSHOT")

    implementation("javax.xml.bind:jaxb-api")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "spock-core")
        exclude(module = "junit-vintage-engine")
    }
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.spockframework:spock-spring:2.3-groovy-4.0") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation("org.apache.groovy:groovy-all:4.0.19")
    testImplementation("org.mock-server:mockserver-netty:5.15.0")
    testImplementation("org.mock-server:mockserver-spring-test-listener:5.15.0")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockftpserver:MockFtpServer:3.1.0")
    testImplementation("io.github.origin-energy:java-snapshot-testing-spock:4.0.7")
    testImplementation("io.github.origin-energy:java-snapshot-testing-plugin-jackson:4.0.7")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
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
            html.required.set(false)
            csv.required.set(false)
        }
    }

    check {
        dependsOn(jacocoTestReport)
    }
}
