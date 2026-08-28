plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val tramaiVersion: String by project

dependencies {
    implementation(platform("dev.tramai:tramai-bom:${tramaiVersion}"))
    // The sovereign Spring Boot starter owns ALL default sovereign
    // infrastructure (model registry, audit/approval/continuation stores,
    // approval gate coordinator, digesters, SovereignTramaiRuntime) and
    // collects ModelProvider + TramaiTool beans from the application
    // context (upstream tramAI PR #268).
    implementation("dev.tramai:tramai-spring-boot-starter-sovereign:${tramaiVersion}")
    // OpenTelemetry operation spans and metrics for TramAI model attempts.
    // Exporter/collector configuration remains the deployment's responsibility.
    implementation("dev.tramai:tramai-observability:${tramaiVersion}")
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    // OpenAI-compatible provider adapter — used only for the OPTIONAL
    // real-model path (KTCONF_DEMO_LOCAL_BASE_URL set).
    implementation("dev.tramai:tramai-openai:${tramaiVersion}")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // suspend controller support in Spring MVC requires the reactive adapter.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("dev.tramai:tramai-standalone:${tramaiVersion}")
    testImplementation("dev.tramai:tramai-testing:${tramaiVersion}")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.bootJar {
    archiveFileName.set("ktconf-demo.jar")
}

tasks.test {
    useJUnitPlatform()
}
