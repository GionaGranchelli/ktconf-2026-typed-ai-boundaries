package dev.giona.ktconf.application

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification
import dev.tramai.sovereign.SovereignTramaiRuntime
import org.springframework.stereotype.Service

/**
 * Ordinary application service. Three separate concerns stay visible:
 *
 * 1. CLASSIFICATION — supplied by the request/upstream (never inferred by
 *    the model). "Classification is supplied."
 * 2. ROUTING — this tiny `when` selects the normal model route.
 *    "Routing chooses."
 * 3. POLICY ENFORCEMENT — TramAI validates whether the selected route is
 *    allowed for that classification. "Policy enforces."
 *
 * A HIGH-risk tool request suspends the workflow: TramAI raises
 * [ApprovalSuspendedException], the approval is registered server-side, and
 * the HTTP request finishes with 202. The workflow did not.
 */
@Service
class InvoiceService(
    runtime: SovereignTramaiRuntime,
    private val registry: PendingApprovalRegistry,
) {
    private val ai: InvoiceAnalysisService = runtime.create(InvoiceAnalysisService::class)

    /**
     * DEMO-ONLY boundary proof: intentionally send a RESTRICTED document
     * through the cloud operation, using the SAME runtime and SAME operation
     * as normal routing. TramAI must deny it before provider invocation
     * (HTTP 403, cloud invocation count stays 0). This is fault injection,
     * NOT production routing logic.
     */
    suspend fun analyzeRestrictedViaCloud(request: AnalyzeInvoiceRequest): InvoiceAssessment =
        ai.analyzeCloud(request.toClassifiedDocument())

    suspend fun analyze(request: AnalyzeInvoiceRequest): AnalyzeOutcome {
        val document = request.toClassifiedDocument()
        return try {
            val (assessment, model, zone) = when (document.classification) {
                DataClassification.RESTRICTED ->
                    Triple(ai.analyzeLocal(document), "local-invoice-model", "LOCAL")

                else ->
                    Triple(ai.analyzeCloud(document), "cloud-invoice-model", "GLOBAL_CLOUD")
            }
            AnalyzeOutcome.Typed(assessment, selectedModel = model, selectedProviderZone = zone)
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

/** Classification is an explicit governance fact on every request. */
data class AnalyzeInvoiceRequest(
    val classification: DataClassification,
    val invoice: dev.giona.ktconf.domain.InvoiceDocument,
)

fun AnalyzeInvoiceRequest.toClassifiedDocument(): ClassifiedDocument<dev.giona.ktconf.domain.InvoiceDocument> =
    ClassifiedDocument(
        payload = invoice,
        classification = classification,
        source = ClassificationSource.DECLARED,
    )

sealed interface AnalyzeOutcome {
    data class Typed(
        val assessment: InvoiceAssessment,
        val selectedModel: String,
        val selectedProviderZone: String,
    ) : AnalyzeOutcome

    data class AwaitingApproval(
        val approvalId: String,
        val workflowRunId: String,
        val toolName: String,
    ) : AnalyzeOutcome
}
