package dev.giona.ktconf.api

import dev.giona.ktconf.demo.ScriptedProvider
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Observable governance counters — the proofs become visible on stage:
 * the cloud provider was NEVER invoked for RESTRICTED data, and the
 * payment executed exactly once after approval.
 */
@RestController
@RequestMapping("/governance")
class GovernanceStatsController(
    private val ledger: InMemoryPaymentLedger,
    private val environment: Environment,
) {

    // Present only in the cloud-routing profile; elsewhere there is no
    // cloud provider to count.
    @Autowired(required = false)
    @Qualifier("cloudProvider")
    private var cloudProvider: ScriptedProvider? = null

    @GetMapping("/stats")
    fun stats(): StatsResponse = StatsResponse(
        cloudInvocationCount = cloudProvider?.invocationCount() ?: 0,
        paymentExecutionCount = ledger.executionCount(),
    )

    /** Minimal liveness probe for stage-up — no Actuator, no dependencies.
     *  Reports the ACTIVE PROFILE so stage-up can prove the right instance
     *  (not a stale one) answers on the port. */
    @GetMapping("/healthz")
    fun healthz(): Map<String, String> = mapOf(
        "status" to "ok",
        "profile" to (environment.activeProfiles.firstOrNull() ?: "none"),
    )
}

data class StatsResponse(
    val cloudInvocationCount: Int,
    val paymentExecutionCount: Int,
)
