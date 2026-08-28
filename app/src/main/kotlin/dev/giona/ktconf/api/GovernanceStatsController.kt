package dev.giona.ktconf.api

import dev.giona.ktconf.governance.CountingModelProvider
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.tramai.core.provider.ModelProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Observable governance counters — the proofs become visible on stage:
 * the cloud provider was NEVER invoked for RESTRICTED data (invocation
 * delta 0), and the payment executed exactly once after approval.
 */
@RestController
@RequestMapping("/governance")
class GovernanceStatsController(
    @Qualifier("cloudProvider") private val cloudProvider: CountingModelProvider,
    @Qualifier("localProvider") private val localProvider: ModelProvider,
    private val ledger: InMemoryPaymentLedger,
) {

    @GetMapping("/stats")
    fun stats(): StatsResponse = StatsResponse(
        cloudInvocationCount = cloudProvider.invocationCount(),
        paymentExecutionCount = ledger.executionCount(),
    )

    /** Minimal liveness probe for stage-up — no Actuator, no dependencies. */
    @GetMapping("/healthz")
    fun healthz(): Map<String, String> = mapOf("status" to "ok")
}

data class StatsResponse(
    val cloudInvocationCount: Int,
    val paymentExecutionCount: Int,
)
