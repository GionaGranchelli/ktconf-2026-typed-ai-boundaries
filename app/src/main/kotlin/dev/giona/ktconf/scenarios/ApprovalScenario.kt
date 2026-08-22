package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.DemoResponses
import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.runtime.DemoRuntimeFactory
import dev.giona.ktconf.runtime.LOCAL_PROVIDER
import dev.giona.ktconf.runtime.local
import dev.giona.ktconf.tools.InMemoryPaymentLedger
import dev.tramai.core.approval.ApprovalTransition
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.engine.ResumeApprovalCommand

/**
 * Scenario 4/5 — High-risk capability + approval lifecycle.
 *
 * The model requests schedule-payment (HIGH risk, HUMAN_REQUIRED).
 * TramAI suspends the workflow before the tool executes; the presenter
 * decides approve / deny / abort; resume goes through TramAI's real
 * continuation mechanism with the engine-supplied idempotency key.
 *
 * Exactly-once claim (demo-scoped): after approval the ledger executes
 * exactly once; a duplicate resume attempt must not double-execute.
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
                service.analyze(DemoInvoices.catering)
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
        runtime: dev.giona.ktconf.runtime.DemoRuntime,
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

        val assessment = runtime.runtime.resumeApprovalTyped<dev.giona.ktconf.domain.InvoiceAssessment>(command)
        val after = ledger.executionCount()
        check(after == 1) { "expected exactly one payment execution after approval, got $after" }

        // Duplicate resume attempt must fail and must NOT double-execute.
        val (resumeFailed, ledgerAfterDuplicate) = try {
            runtime.runtime.resumeApprovalTyped<dev.giona.ktconf.domain.InvoiceAssessment>(command)
            false to ledger.executionCount()
        } catch (_: Exception) {
            true to ledger.executionCount()
        }
        check(!resumeFailed || ledgerAfterDuplicate == 1) {
            "duplicate resume must not double-execute; ledger=$ledgerAfterDuplicate"
        }

        return ApprovalResult(
            suspension = suspension,
            ledgerBeforeDecision = 0,
            decision = ApprovalDecision.APPROVE,
            ledgerAfterDecision = after,
            ledgerAfterDuplicateResume = ledgerAfterDuplicate,
            assessment = assessment,
            resumeFailure = resumeFailed,
        )
    }

    private suspend fun deny(
        runtime: dev.giona.ktconf.runtime.DemoRuntime,
        suspension: ApprovalSuspendedException,
        expectedVersion: Long,
        ledger: InMemoryPaymentLedger,
    ): ApprovalResult {
        runtime.approvalStore.transition(
            suspension.approvalId,
            expectedVersion,
            ApprovalTransition.Deny(
                decidedBy = "demo-operator",
                comment = "Denied by presenter",
            ),
        )
        val command = ResumeApprovalCommand(
            approvalId = suspension.approvalId,
            approvalExpectedVersion = expectedVersion + 1,
            continuationExpectedVersion = suspension.continuationVersion,
            presentedToken = suspension.challenge.token,
            resumedBy = "demo-operator",
        )
        val resumeFailed = try {
            runtime.runtime.resumeApprovalTyped<dev.giona.ktconf.domain.InvoiceAssessment>(command)
            false
        } catch (_: Exception) {
            true
        }
        check(resumeFailed) { "resume after deny must fail" }
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
