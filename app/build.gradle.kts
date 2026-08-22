plugins {
    kotlin("jvm") version "2.3.0"
    application
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
    implementation("dev.tramai:tramai-core:${tramaiVersion}")
    implementation("dev.tramai:tramai-engine:${tramaiVersion}")
    implementation("dev.tramai:tramai-security:${tramaiVersion}")
    implementation("dev.tramai:tramai-structured:${tramaiVersion}")
    implementation("dev.tramai:tramai-sovereign:${tramaiVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

dependencyLocking {
    lockAllConfigurations()
}

application {
    mainClass = "dev.giona.ktconf.MainKt"
    applicationName = "ktconf-demo"
}

tasks.test {
    useJUnitPlatform()
}
