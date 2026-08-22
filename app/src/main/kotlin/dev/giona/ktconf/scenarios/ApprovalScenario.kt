package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.DemoResponses
import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.runtime.DemoRuntime
import dev.giona.ktconf.runtime.DemoRuntimeFactory
import dev.giona.ktconf.runtime.LOCAL_PROVIDER
import dev.giona.ktconf.runtime.local
import dev.giona.ktconf.tools.InMemoryPaymentLedger
import dev.tramai.core.approval.ApprovalTransition
import dev.tramai.core.exception.ApprovalAuthorizationException
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.exception.ApprovalTokenRejectedException
import dev.tramai.engine.ResumeApprovalCommand

/**
 * Scenario 4/5 — High-risk capability + approval lifecycle.
 *
 * The model requests schedule-payment for the payment invoice
 * (KTCONF-PAY-001, HIGH risk, HUMAN_REQUIRED). TramAI suspends the
 * workflow before the tool executes; the presenter decides
 * approve / deny / abort; resume goes through TramAI's real continuation
 * mechanism with the engine-supplied idempotency key.
 *
 * Exactly-once claim (demo-scoped): after approval the ledger executes
 * exactly once; a duplicate resume attempt must be rejected by TramAI
 * (ApprovalTokenRejectedException — continuation already completed) and
 * must not double-execute. Any OTHER exception propagates and kills the
 * scenario loudly — expected boundary failures are never conflated with
 * unexpected demo failures.
 */
class ApprovalScenario(
    private val factory: DemoRuntimeFactory = DemoRuntimeFactory(),
) {

    suspend fun run(decision: ApprovalDecision): ApprovalResult {
        val ledger = InMemoryPaymentLedger()
        factory.local(
            provider = ScriptedProvider(LOCAL_PROVIDER, DemoResponses.paymentFlow),
            ledger = ledger,
        ).use { runtime ->
            val service = runtime.runtime.create(InvoiceAnalysisService::class)

            val suspension = try {
                service.analyze(DemoInvoices.paymentInvoice)
                error("Expected ApprovalSuspendedException for HIGH-risk tool")
            } catch (e: ApprovalSuspendedException) {
                e
            }
            val ledgerBefore = ledger.executionCount()
            check(ledgerBefore == 0) { "tool must not execute before approval" }

            val stored = runtime.approvalStore.get(suspension.approvalId)
                ?: error("Approval ${suspension.approvalId} missing from store")

            return when (decision) {
                ApprovalDecision.APPROVE -> approve(runtime, suspension, stored.version, ledger)
                ApprovalDecision.DENY -> deny(runtime, suspension, stored.version, ledger)
                ApprovalDecision.ABORT -> ApprovalResult(
                    suspension = suspension,
                    ledgerBeforeDecision = ledgerBefore,
                    decision = ApprovalDecision.ABORT,
                    ledgerAfterDecision = ledger.executionCount(),
                    ledgerAfterDuplicateResume = ledger.executionCount(),
                    assessment = null,
                    resumeFailure = false,
                )
            }
        }
    }

    private suspend fun approve(
        runtime: DemoRuntime,
        suspension: ApprovalSuspendedException,
        expectedVersion: Long,
        ledger: InMemoryPaymentLedger,
    ): ApprovalResult {
        val approved = runtime.approvalStore.transition(
            suspension.approvalId,
            expectedVersion,
            ApprovalTransition.Approve(
                decidedBy = "demo-operator",
                comment = "Approved by presenter",
            ),
        )
        val command = ResumeApprovalCommand(
            approvalId = suspension.approvalId,
            approvalExpectedVersion = approved.version,
            continuationExpectedVersion = suspension.continuationVersion,
            presentedToken = suspension.challenge.token,
            resumedBy = "demo-operator",
        )

        val assessment = runtime.runtime.resumeApprovalTyped<InvoiceAssessment>(command)
        val after = ledger.executionCount()
        check(after == 1) { "expected exactly one payment execution after approval, got $after" }

        // Duplicate resume: TramAI must reject it (continuation already
        // COMPLETED → ApprovalTokenRejectedException) and the ledger must
        // not double-execute. Anything else propagates — loudly.
        val (resumeFailed, ledgerAfterDuplicate) = try {
            runtime.runtime.resumeApprovalTyped<InvoiceAssessment>(command)
            false to ledger.executionCount()
        } catch (_: ApprovalTokenRejectedException) {
            true to ledger.executionCount()
        }
        check(resumeFailed) { "duplicate resume must be rejected by TramAI" }
        check(ledgerAfterDuplicate == 1) {
            "duplicate resume must not double-execute; ledger=$ledgerAfterDuplicate"
        }

        return ApprovalResult(
            suspension = suspension,
            ledgerBeforeDecision = 0,
            decision = ApprovalDecision.APPROVE,
            ledgerAfterDecision = after,
            ledgerAfterDuplicateResume = ledgerAfterDuplicate,
            assessment = assessment,
            resumeFailure = true,
        )
    }

    private suspend fun deny(
        runtime: DemoRuntime,
        suspension: ApprovalSuspendedException,
        expectedVersion: Long,
        ledger: InMemoryPaymentLedger,
    ): ApprovalResult {
        val denied = runtime.approvalStore.transition(
            suspension.approvalId,
            expectedVersion,
            ApprovalTransition.Deny(
                decidedBy = "demo-operator",
                comment = "Denied by presenter",
            ),
        )
        val command = ResumeApprovalCommand(
            approvalId = suspension.approvalId,
            approvalExpectedVersion = denied.version,
            continuationExpectedVersion = suspension.continuationVersion,
            presentedToken = suspension.challenge.token,
            resumedBy = "demo-operator",
        )
        // Denied approval resume: the store rejects consumption
        // (ApprovalStoreNotConsumableException → ApprovalAuthorizationException).
        // Any other exception propagates — loudly.
        val resumeFailed = try {
            runtime.runtime.resumeApprovalTyped<InvoiceAssessment>(command)
            false
        } catch (_: ApprovalAuthorizationException) {
            true
        }
        check(resumeFailed) { "resume after deny must be rejected by TramAI" }
        check(ledger.executionCount() == 0) { "denied approval must not execute payment" }

        return ApprovalResult(
            suspension = suspension,
            ledgerBeforeDecision = 0,
            decision = ApprovalDecision.DENY,
            ledgerAfterDecision = ledger.executionCount(),
            ledgerAfterDuplicateResume = ledger.executionCount(),
            assessment = null,
            resumeFailure = true,
        )
    }
}
