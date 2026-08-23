package dev.giona.ktconf.application

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.tramai.core.approval.ApprovalStore
import dev.tramai.core.approval.ApprovalTransition
import dev.tramai.core.exception.ApprovalAuthorizationException
import dev.tramai.core.exception.ApprovalNotFoundException
import dev.tramai.engine.ResumeApprovalCommand
import dev.tramai.sovereign.SovereignTramaiRuntime
import org.springframework.stereotype.Service

/**
 * Approve / deny a suspended workflow through TramAI's real continuation
 * mechanism. The registry supplies the server-side challenge token; nothing
 * token-shaped ever crosses the REST boundary.
 *
 * The approval store and the runtime are beans auto-configured by the
 * sovereign starter — the application only orchestrates the HTTP lifecycle.
 *
 * Deny preserves the v3 oracle: transition DENIED → resume attempted →
 * TramAI itself refuses continuation ([ApprovalAuthorizationException]) →
 * payment remains 0. That proves the RUNTIME refused, not merely that we
 * skipped execution.
 */
@Service
class ApprovalService(
    private val registry: PendingApprovalRegistry,
    private val approvalStore: ApprovalStore,
    private val runtime: SovereignTramaiRuntime,
    private val ledger: InMemoryPaymentLedger,
) {

    suspend fun approve(approvalId: String): InvoiceAssessment {
        val pending = registry.require(approvalId)
        val stored = approvalStore.get(approvalId) ?: throw ApprovalNotFoundException(approvalId)
        val approved = approvalStore.transition(
            approvalId,
            stored.version,
            ApprovalTransition.Approve(decidedBy = "demo-operator", comment = "Approved via HTTP"),
        )
        val command = ResumeApprovalCommand(
            approvalId = approvalId,
            approvalExpectedVersion = approved.version,
            continuationExpectedVersion = pending.continuationVersion,
            presentedToken = pending.presentedToken,
            resumedBy = "demo-operator",
        )
        // Resume through the SAME managed runtime the analyze used. On
        // success mark COMPLETED; unexpected failures propagate loudly and
        // do NOT rewrite registry state — the store holds the authoritative
        // transition (APPROVED), so any retry is rejected there (409).
        val assessment = runtime.resumeApprovalTyped<InvoiceAssessment>(command)
        registry.complete(approvalId, PendingApprovalRegistry.State.COMPLETED)
        return assessment
    }

    suspend fun deny(approvalId: String): DenyOutcome {
        val pending = registry.require(approvalId)
        val stored = approvalStore.get(approvalId) ?: throw ApprovalNotFoundException(approvalId)
        val denied = approvalStore.transition(
            approvalId,
            stored.version,
            ApprovalTransition.Deny(decidedBy = "demo-operator", comment = "Denied via HTTP"),
        )
        val command = ResumeApprovalCommand(
            approvalId = approvalId,
            approvalExpectedVersion = denied.version,
            continuationExpectedVersion = pending.continuationVersion,
            presentedToken = pending.presentedToken,
            resumedBy = "demo-operator",
        )
        // v2 oracle: resume after deny must be REJECTED by TramAI, and the
        // ledger must not gain an entry for this workflow (per-workflow proof:
        // count before the resume attempt is preserved after the rejection).
        val executionsBeforeResume = ledger.executionCount()
        val rejected = try {
            runtime.resumeApprovalTyped<InvoiceAssessment>(command)
            false
        } catch (_: ApprovalAuthorizationException) {
            true
        }
        check(rejected) { "resume after deny must be rejected by TramAI" }
        check(ledger.executionCount() == executionsBeforeResume) {
            "denied approval must not execute payment (count ${ledger.executionCount()} != $executionsBeforeResume)"
        }
        registry.complete(approvalId, PendingApprovalRegistry.State.DENIED)
        return DenyOutcome(
            approvalId = approvalId,
            status = "DENIED",
            paymentExecutionCount = ledger.executionCount(),
        )
    }
}

data class DenyOutcome(
    val approvalId: String,
    val status: String,
    val paymentExecutionCount: Int,
)
