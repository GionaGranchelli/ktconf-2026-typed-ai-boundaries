package dev.giona.ktconf.api

import dev.giona.ktconf.governance.CountingModelProvider
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.giona.ktconf.notifications.FakeEmailService
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
    @param:Qualifier("cloudProvider") private val cloudProvider: CountingModelProvider,
    @param:Qualifier("localProvider") private val localProvider: CountingModelProvider,
    private val ledger: InMemoryPaymentLedger,
    private val email: FakeEmailService,
) {

    @GetMapping("/stats")
    fun stats(): StatsResponse = StatsResponse(
        cloudInvocationCount = cloudProvider.invocationCount(),
        localInvocationCount = localProvider.invocationCount(),
        paymentExecutionCount = ledger.executionCount(),
        emailNotificationCount = email.count(),
    )

    /** Minimal liveness probe for stage-up — no Actuator, no dependencies. */
    @GetMapping("/healthz")
    fun healthz(): Map<String, String> = mapOf("status" to "ok")
}

data class StatsResponse(
    val cloudInvocationCount: Int,
    val localInvocationCount: Int,
    val paymentExecutionCount: Int,
    val emailNotificationCount: Int = 0,
)
