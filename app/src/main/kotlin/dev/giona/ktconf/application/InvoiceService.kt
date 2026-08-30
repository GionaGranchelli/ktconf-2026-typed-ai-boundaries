package dev.giona.ktconf.application

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.policy.DataClassification
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

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
    private val ai: InvoiceAnalysisService,
    private val registry: PendingApprovalRegistry,
    private val telemetry: GovernanceTelemetry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(request: AnalyzeInvoiceRequest): AnalyzeOutcome {
        val document = request.toClassifiedDocument()
        val route = when (document.classification) {
                DataClassification.PUBLIC,
                DataClassification.INTERNAL, -> InvoiceRoute.CLOUD

                DataClassification.CONFIDENTIAL,
                DataClassification.RESTRICTED, -> InvoiceRoute.LOCAL
            }
        return telemetry.traceModelCall(document.classification, route) {
            try {
                AnalyzeOutcome.Typed(
                    assessment = when (route) {
                        InvoiceRoute.CLOUD -> ai.analyzeCloud(document)
                        InvoiceRoute.LOCAL -> ai.analyzeLocal(document)
                    },
                    selectedRoute = route,
                )
            } catch (e: ApprovalSuspendedException) {
                log.info("Workflow suspended by approval gate: approvalId={}, workflowRunId={}, tool={}", e.approvalId, e.workflowRunId, e.toolName)
                val pending = registry.register(e)
                AnalyzeOutcome.AwaitingApproval(
                    approvalId = pending.approvalId,
                    workflowRunId = pending.workflowRunId,
                    toolName = pending.toolName,
                    rationale = "Payment scheduling requires human approval because invoice ${document.payload.invoiceId} is a high-risk write action.",
                )
            }
        }
    }

    /**
     * DEMO-ONLY boundary proof: intentionally send a RESTRICTED document
     * through the cloud operation, using the SAME runtime and SAME operation
     * as normal routing. TramAI must deny it before provider invocation
     * (HTTP 403, cloud invocation delta = 0). This is fault injection,
     * NOT production routing logic.
     */
    suspend fun analyzeRestrictedViaCloud(request: AnalyzeInvoiceRequest): InvoiceAssessment {
        val document = request.toClassifiedDocument()
        return telemetry.traceModelCall(document.classification, InvoiceRoute.CLOUD) {
            ai.analyzeCloud(document)
        }.also {
            log.error("Boundary proof unexpectedly completed: invoiceId={} reached cloud operation", request.invoice.invoiceId)
        }
    }
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
        val rationale: String
    ) : AnalyzeOutcome
}
