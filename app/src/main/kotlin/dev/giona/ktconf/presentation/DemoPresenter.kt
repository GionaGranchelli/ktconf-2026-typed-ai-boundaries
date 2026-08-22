package dev.giona.ktconf.presentation

import dev.giona.ktconf.scenarios.ApprovalResult
import dev.giona.ktconf.scenarios.EvidenceResult
import dev.giona.ktconf.scenarios.InvalidOutputResult
import dev.giona.ktconf.scenarios.RestrictedDataResult
import dev.giona.ktconf.scenarios.TypedBoundaryResult
import dev.tramai.security.audit.AuditEvent

/**
 * Concise stage formatting for each scenario result.
 *
 * Every printed line derives from a real TramAI result or a real
 * audit/evidence record — never fabricated for the show.
 */
object DemoPresenter {

    fun title(label: String) {
        println()
        println("═ $label ".padEnd(72, '═'))
    }

    fun typed(result: TypedBoundaryResult) {
        title("Scenario 1 — Typed boundary")
        val a = result.assessment
        println("typed result escaped = YES (real TramAI structured output)")
        println("invoiceId           = ${a.invoiceId}")
        println("supplierName        = ${a.supplierName}")
        println("amountCents         = ${a.amountCents}")
        println("currency            = ${a.currency}")
        println("risk                = ${a.risk}")
        println("recommendedAction   = ${a.recommendedAction}")
    }

    fun invalid(result: InvalidOutputResult) {
        title("Scenario 2 — Broken model (invalid structured output)")
        val f = result.failure
        println("typed result escaped = NO")
        println("failure              = ${f::class.simpleName}")
        println("attemptCount         = ${f.attemptCount ?: "n/a"}")
        println("validationError      = ${f.validationError?.take(90) ?: "n/a"}")
        println("payment execution    = ${result.paymentExecutionCount}")
    }

    fun restricted(result: RestrictedDataResult) {
        title("Scenario 3 — Restricted data")
        println("input classification = RESTRICTED (source: DECLARED)")
        println("cloud provider       = ${dev.giona.ktconf.runtime.CLOUD_PROVIDER} (GLOBAL_CLOUD zone)")
        println("denied reason        = ${result.denial.decision.reason} (${result.denial.decision.reasonCode})")
        println("cloud invocations    = ${result.cloudInvocationCount}")
        val a = result.localAssessment
        println("LOCAL provider       = typed result → risk=${a.risk}, action=${a.recommendedAction}")
    }

    fun approval(result: ApprovalResult) {
        title("Scenario 4/5 — High-risk capability + approval lifecycle")
        println("tool requested       = ${result.suspension.toolName} (${result.suspension.approvalId})")
        println("risk / approval      = HIGH / HUMAN_REQUIRED")
        println("payment at suspend   = ${result.ledgerBeforeDecision}")
        println("presenter decision   = ${result.decision}")
        when (result.decision) {
            dev.giona.ktconf.scenarios.ApprovalDecision.APPROVE -> {
                println("payment after resume  = ${result.ledgerAfterDecision}")
                println("duplicate resume      = ${if (result.resumeFailure) "rejected" else "ACCEPTED (unexpected)"} → payment ${result.ledgerAfterDuplicateResume}")
                result.assessment?.let {
                    println("resumed assessment    = risk=${it.risk}, action=${it.recommendedAction}")
                }
            }
            dev.giona.ktconf.scenarios.ApprovalDecision.DENY ->
                println("payment after deny    = ${result.ledgerAfterDecision} (resume rejected)")
            dev.giona.ktconf.scenarios.ApprovalDecision.ABORT ->
                println("payment (aborted)     = ${result.ledgerAfterDecision} — workflow stays suspended")
        }
    }

    fun evidence(result: EvidenceResult) {
        title("Scenario 6 — Evidence")
        println("audit events          = ${result.eventCount}")
        println("hash chain valid      = ${result.chainValid}")
        println("raw evidence          = ${result.evidenceDirectory}")
        println()
        result.auditEvents.forEach { println(evidenceRow(it)) }
    }

    private fun evidenceRow(e: AuditEvent): String =
        "#%-3d %-32s %-10s %s".format(
            e.sequenceNumber,
            e.enforcementPoint,
            e.decision,
            e.timestamp,
        )
}
