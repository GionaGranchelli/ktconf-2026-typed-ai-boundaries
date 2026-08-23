package dev.giona.ktconf.application

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.policy.DataClassification
import dev.tramai.sovereign.SovereignTramaiRuntime
import org.springframework.stereotype.Service

/**
 * Ordinary application service. Three separate concerns stay visible:
 *
 * 1. CLASSIFICATION — supplied by the request/upstream (never inferred by
 *    the model). "Classification is supplied."
 * 2. ROUTING — this exhaustive `when` selects the normal model route.
 *    "Routing chooses."
 * 3. POLICY ENFORCEMENT — TramAI validates whether the selected route is
 *    allowed for that classification. "Policy enforces."
 *
 * The route is an APPLICATION-owned choice ([InvoiceRoute]); the YAML owns
 * model → provider → trust zone. TramAI stays the independent backstop: if
 * the application routes CONFIDENTIAL/RESTRICTED data to cloud anyway,
 * TramAI denies it before provider invocation.
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

    suspend fun analyze(request: AnalyzeInvoiceRequest): AnalyzeOutcome {
        val document = request.toClassifiedDocument()
        return try {
            when (document.classification) {
                DataClassification.PUBLIC,
                DataClassification.INTERNAL,
                -> AnalyzeOutcome.Typed(ai.analyzeCloud(document), InvoiceRoute.CLOUD)

                DataClassification.CONFIDENTIAL,
                DataClassification.RESTRICTED,
                -> AnalyzeOutcome.Typed(ai.analyzeLocal(document), InvoiceRoute.LOCAL)
            }
        } catch (e: ApprovalSuspendedException) {
            val pending = registry.register(e)
            AnalyzeOutcome.AwaitingApproval(
                approvalId = pending.approvalId,
                workflowRunId = pending.workflowRunId,
                toolName = pending.toolName,
            )
        }
    }

    /**
     * DEMO-ONLY boundary proof: intentionally send a RESTRICTED document
     * through the cloud operation, using the SAME runtime and SAME operation
     * as normal routing. TramAI must deny it before provider invocation
     * (HTTP 403, cloud invocation delta = 0). This is fault injection,
     * NOT production routing logic.
     */
    suspend fun analyzeRestrictedViaCloud(request: AnalyzeInvoiceRequest): InvoiceAssessment =
        ai.analyzeCloud(request.toClassifiedDocument())
}

/**
 * Application-owned route choice. The YAML (not this enum) decides which
 * model/provider/trust zone backs each route.
 */
enum class InvoiceRoute { LOCAL, CLOUD }

sealed interface AnalyzeOutcome {
    data class Typed(
        val assessment: InvoiceAssessment,
        val selectedRoute: InvoiceRoute,
    ) : AnalyzeOutcome

    data class AwaitingApproval(
        val approvalId: String,
        val workflowRunId: String,
        val toolName: String,
    ) : AnalyzeOutcome
}
