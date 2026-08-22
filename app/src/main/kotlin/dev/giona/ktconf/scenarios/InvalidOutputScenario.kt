package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.DemoResponses
import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.runtime.DemoRuntimeFactory
import dev.giona.ktconf.runtime.LOCAL_PROVIDER
import dev.giona.ktconf.runtime.local
import dev.giona.ktconf.tools.InMemoryPaymentLedger
import dev.tramai.core.exception.StructuredOutputException

/**
 * Scenario 2 — Broken model.
 *
 * A deterministic provider returns deliberately invalid structured output
 * ("banana" amount, "YOLO" risk, "MAYBE_PAY" action). TramAI's
 * structured-output engine must reject it. The demo does NOT validate
 * the JSON anywhere — the engine does.
 */
class InvalidOutputScenario(
    private val factory: DemoRuntimeFactory = DemoRuntimeFactory(),
) {
    suspend fun run(): InvalidOutputResult {
        val ledger = InMemoryPaymentLedger()
        factory.local(
            provider = ScriptedProvider(LOCAL_PROVIDER, listOf(DemoResponses.brokenAssessment)),
            ledger = ledger,
        ).use { runtime ->
            val service = runtime.runtime.create(InvoiceAnalysisService::class)
            val failure = try {
                service.analyze(DemoInvoices.catering)
                error("Expected StructuredOutputException for broken model output")
            } catch (e: StructuredOutputException) {
                e
            }
            check(ledger.executionCount() == 0) { "no payment may execute after rejected output" }
            return InvalidOutputResult(
                failure = failure,
                paymentExecutionCount = ledger.executionCount(),
            )
        }
    }
}
