package dev.giona.ktconf

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * KTConf 2026 — Typed AI Boundaries.
 *
 * ONE Spring Boot application, ONE process, ONE port, ONE
 * SovereignTramaiRuntime with TWO model/provider routes (local + cloud).
 * The TramAI integration is visible in four places:
 *  1. application.yml                  — policy configuration
 *  2. ai/InvoiceAnalysisService.kt     — the typed @AiService boundary
 *  3. application/InvoiceService.kt    — routing (when classification)
 *  4. payments/SchedulePaymentTool.kt  — tool = authority
 * Everything else is ordinary Spring.
 */
@SpringBootApplication
class SpringDemoApplication

fun main(args: Array<String>) {
    runApplication<SpringDemoApplication>(*args)
}
