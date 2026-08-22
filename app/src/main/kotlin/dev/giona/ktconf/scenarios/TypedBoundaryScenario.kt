package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.DemoResponses
import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.runtime.DemoRuntimeFactory
import dev.giona.ktconf.runtime.LOCAL_PROVIDER
import dev.giona.ktconf.runtime.local

/**
 * Scenario 1 — Typed boundary.
 *
 * A deterministic provider returns valid structured output; real TramAI
 * structured-output processing turns it into an [InvoiceAssessment].
 * No manual JSON mapping anywhere in application code.
 */
class TypedBoundaryScenario(
    private val factory: DemoRuntimeFactory = DemoRuntimeFactory(),
) {
    suspend fun run(): TypedBoundaryResult =
        factory.local(
            ScriptedProvider(LOCAL_PROVIDER, listOf(DemoResponses.cateringAssessment)),
        ).use { runtime ->
            val service = runtime.runtime.create(InvoiceAnalysisService::class)
            val assessment = service.analyze(DemoInvoices.catering)
            check(assessment.risk == InvoiceRisk.LOW) { "expected risk LOW, got ${assessment.risk}" }
            check(assessment.recommendedAction == InvoiceAction.REVIEW_ONLY) {
                "expected REVIEW_ONLY, got ${assessment.recommendedAction}"
            }
            TypedBoundaryResult(assessment)
        }
}
