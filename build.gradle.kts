buildscript {
    dependencies {
        // Plugin dependencies use a separate classpath from application dependencies.
        classpath(platform(libs.jackson.bom))
        classpath(platform(libs.log4j.bom))
        constraints {
            classpath(libs.plexus.utils) { because("CVE-2025-67030: fix archive path traversal in build plugins") }
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.devapplab"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

configurations.configureEach {
    // CVE-2026-8484: unmaintained Jansi has no patched release.
    // CallLogging uses a plain-text formatter without its optional ANSI support.
    exclude(group = "org.fusesource.jansi", module = "jansi")
}

dependencies {
    // Keep shared transitive modules aligned across Ktor, Firebase, and Cloudinary.
    implementation(platform(libs.netty.bom))
    implementation(platform(libs.micrometer.bom))
    implementation(platform(libs.opentelemetry.bom))
    implementation(platform(libs.jackson.bom))
    constraints {
        implementation(libs.java.jwt) { because("Includes JWT parsing security fixes and updated Jackson dependencies") }
        implementation(libs.httpclient5) { because("Includes HTTP client security fixes") }
        implementation(libs.httpcore5) { because("Keep HTTP/1 and HTTP/2 on the patched HttpCore release") }
        implementation(libs.httpcore5.h2) { because("Keep HTTP/1 and HTTP/2 on the patched HttpCore release") }
    }
    implementation(libs.ktor.server.rate.limiting)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.brcypt)
    implementation(libs.logback.classic)
    implementation(libs.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.firebase.admin)
    implementation(libs.jwks.rsa)
    implementation(platform(libs.grpc.bom))
    implementation(libs.cloudinary)
    // Security fix for CVE-2025-48924
    implementation(libs.commons.lang3)
    implementation(libs.stripe.java)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

}
