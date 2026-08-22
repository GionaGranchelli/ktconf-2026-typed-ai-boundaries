package dev.giona.ktconf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * KTConf 2026 — Typed AI Boundaries, Spring Boot variant.
 *
 * The SAME demo as the frozen v2 CLI (main), rebuilt as an ordinary Kotlin
 * backend: profiles select the model infrastructure bean, the REST surface
 * exposes the typed boundary, and every governance guarantee from v2 is
 * preserved as acceptance criteria. See docs/DEMO-SCRIPT.md.
 */
@SpringBootApplication
class SpringDemoApplication

fun main(args: Array<String>) {
    // Default profile is demo; SPRING_PROFILES_ACTIVE (stage-up) overrides it
    // for the broken / cloud-routing / real instances.
    System.setProperty("spring.profiles.default", "demo")
    runApplication<SpringDemoApplication>(*args)
}
