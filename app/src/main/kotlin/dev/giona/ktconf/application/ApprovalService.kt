package dev.giona.ktconf.application

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.tramai.core.approval.ApprovalStore
import dev.tramai.core.approval.ApprovalStatus
import dev.tramai.core.approval.ApprovalTransition
import dev.tramai.core.exception.ApprovalAuthorizationException
import dev.tramai.core.exception.ApprovalNotFoundException
import dev.tramai.engine.ResumeApprovalCommand
import dev.tramai.sovereign.SovereignTramaiRuntime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Approve / deny a suspended workflow through TramAI's real continuation
 * mechanism. The registry supplies the server-side challenge token; nothing
 * token-shaped ever crosses the REST boundary.
 *
 * The approval store and the runtime are beans auto-configured by the
 * sovereign starter — the application only orchestrates the HTTP lifecycle.
 *
 * Deny: transition DENIED → resume attempted → TramAI itself refuses
 * continuation ([ApprovalAuthorizationException]) → the application marks
 * the approval denied and returns. The ledger assertion (payment remained
 * unchanged) lives in the tests, not here.
 */
@Service
class ApprovalService(
    private val registry: PendingApprovalRegistry,
    private val workflowApprovals: WorkflowHumanApprovalGateway,
    private val approvalStore: ApprovalStore,
    private val runtime: SovereignTramaiRuntime,
    private val history: DocumentHistoryService,
    private val invoices: InvoiceService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun approve(approvalId: String): InvoiceAssessment {
        log.info("Approving suspended workflow: approvalId={}", approvalId)
        if (workflowApprovals.contains(approvalId)) {
            return workflowApprovals.approve(approvalId)
        }
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
        history.updateApproval(approvalId, "SCHEDULED", assessment)
        log.info("Approved workflow completed: approvalId={}, invoiceId={}, action={}", approvalId, assessment.invoiceId, assessment.recommendedAction)
        return assessment
    }

    suspend fun deny(approvalId: String): DenyOutcome {
        log.info("Denying suspended workflow: approvalId={}", approvalId)
        if (workflowApprovals.contains(approvalId)) {
            workflowApprovals.deny(approvalId)
            return DenyOutcome(approvalId = approvalId, status = "DENIED")
        }
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
        // The runtime itself must refuse continuation after a deny. If it
        // does not, that is a TramAI contract violation — fail loudly.
        try {
            runtime.resumeApprovalTyped<InvoiceAssessment>(command)
            throw IllegalStateException("resume after deny must be rejected by TramAI")
        } catch (_: ApprovalAuthorizationException) {
            // Expected: the runtime refuses the denied continuation.
            log.info("Denied continuation was rejected by TramAI: approvalId={}", approvalId)
        }
        registry.complete(approvalId, PendingApprovalRegistry.State.DENIED)
        history.updateApproval(approvalId, "DENIED")
        log.info("Workflow marked denied: approvalId={}", approvalId)
        return DenyOutcome(
            approvalId = approvalId,
            status = "DENIED",
        )
    }

    /**
     * Reissue is a project-level recovery action. The old TramAI approval is
     * first terminalized as TIMED_OUT; the new analysis creates a new
     * continuation, approval id, email, and audit stream.
     */
    suspend fun reissue(approvalId: String): ReissueOutcome {
        if (workflowApprovals.contains(approvalId)) {
            throw ApprovalReissueNotAllowedException("legacy workflow approvals cannot be reissued")
        }
        val previous = history.findByApprovalId(approvalId)
            ?: throw ApprovalReissueNotFoundException(approvalId)
        if (previous.status != "AWAITING_APPROVAL") {
            throw ApprovalReissueNotAllowedException("approval '$approvalId' is already ${previous.status}")
        }

        registry.require(approvalId)
        val stored = approvalStore.get(approvalId) ?: throw ApprovalNotFoundException(approvalId)
        when (stored.status) {
            ApprovalStatus.PENDING -> {
                if (stored.expiresAt.isAfter(clock.instant())) {
                    throw ApprovalReissueNotAllowedException(
                        "approval '$approvalId' is still active until ${stored.expiresAt}",
                    )
                }
                approvalStore.transition(approvalId, stored.version, ApprovalTransition.Timeout)
            }
            ApprovalStatus.TIMED_OUT -> Unit
            else -> throw ApprovalReissueNotAllowedException("approval '$approvalId' is already ${stored.status}")
        }

        registry.complete(approvalId, PendingApprovalRegistry.State.EXPIRED)
        history.markExpired(approvalId)

        val request = AnalyzeInvoiceRequest(
            classification = previous.metadata.classification,
            invoice = previous.invoice,
        )
        val outcome = invoices.analyze(request, previous.selectedRoute, previous.classificationSource)
        val replacement = history.record(
            invoice = previous.invoice,
            metadata = previous.metadata,
            outcome = outcome,
            reissuedFromApprovalId = approvalId,
        )
        val newApprovalId = (outcome as? AnalyzeOutcome.AwaitingApproval)?.approvalId
        history.linkReissue(approvalId, replacement.id, newApprovalId)
        return ReissueOutcome(
            status = if (newApprovalId == null) "REISSUED_COMPLETED" else "REISSUED_AWAITING_APPROVAL",
            previousApprovalId = approvalId,
            previousDocumentId = previous.id,
            documentId = replacement.id,
            newApprovalId = newApprovalId,
            selectedRoute = replacement.selectedRoute,
        )
    }
}

data class DenyOutcome(
    val approvalId: String,
    val status: String,
)

data class ReissueOutcome(
    val status: String,
    val previousApprovalId: String,
    val previousDocumentId: String,
    val documentId: String,
    val newApprovalId: String?,
    val selectedRoute: InvoiceRoute,
)

class ApprovalReissueNotFoundException(approvalId: String) :
    RuntimeException("No PDF-backed approval history exists for '$approvalId'")

class ApprovalReissueNotAllowedException(message: String) : RuntimeException(message)
