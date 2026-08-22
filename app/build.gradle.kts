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
    implementation("dev.tramai:tramai-core:${tramaiVersion}")
    implementation("dev.tramai:tramai-engine:${tramaiVersion}")
    implementation("dev.tramai:tramai-security:${tramaiVersion}")
    implementation("dev.tramai:tramai-structured:${tramaiVersion}")
    implementation("dev.tramai:tramai-sovereign:${tramaiVersion}")
    implementation("dev.tramai:tramai-openai:${tramaiVersion}")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // suspend controller support in Spring MVC requires the reactive adapter.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
