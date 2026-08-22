package dev.giona.ktconf.application

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification
import org.springframework.stereotype.Service

/**
 * The one coherent entry point for invoice analysis.
 *
 * Classification is a governance fact supplied by the application (never
 * inferred by the model): every demo invoice is declared RESTRICTED/DECLARED.
 * The analyzer port decides which TramAI infrastructure serves the request.
 *
 * A HIGH-risk tool request suspends the workflow — TramAI raises
 * [ApprovalSuspendedException], the pending approval is registered
 * server-side, and the HTTP request finishes with 202. The workflow did not.
 */
@Service
class InvoiceApplicationService(
    private val analyzer: InvoiceAnalyzer,
    private val registry: PendingApprovalRegistry,
) {

    suspend fun analyze(invoice: InvoiceDocument): AnalyzeOutcome {
        val document = ClassifiedDocument(
            payload = invoice,
            classification = DataClassification.RESTRICTED,
            source = ClassificationSource.DECLARED,
        )
        return try {
            AnalyzeOutcome.Typed(analyzer.analyze(document))
        } catch (e: ApprovalSuspendedException) {
            val pending = registry.register(e)
            AnalyzeOutcome.AwaitingApproval(
                approvalId = pending.approvalId,
                workflowRunId = pending.workflowRunId,
                toolName = pending.toolName,
            )
        }
    }
}

sealed interface AnalyzeOutcome {
    data class Typed(val assessment: InvoiceAssessment) : AnalyzeOutcome

    data class AwaitingApproval(
        val approvalId: String,
        val workflowRunId: String,
        val toolName: String,
    ) : AnalyzeOutcome
}
