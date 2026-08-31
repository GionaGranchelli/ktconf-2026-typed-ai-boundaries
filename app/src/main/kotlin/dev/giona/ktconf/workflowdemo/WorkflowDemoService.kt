package dev.giona.ktconf.workflowdemo

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.application.WorkflowHumanApprovalGateway
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.giona.ktconf.notifications.FakeEmailService
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.approval.gateway.ApprovalRequestResult
import dev.tramai.core.policy.DataClassification
import dev.tramai.orchestration.ReplayPolicy
import dev.tramai.orchestration.WorkflowContext
import dev.tramai.orchestration.WorkflowObserver
import dev.tramai.orchestration.workflow
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * Explicit workflow version of the invoice demo:
 * classify -> route -> assess -> apply trusted amount gate -> notify -> finalize.
 *
 * The AI recommends. The workflow owns ordering. TramAI policy still owns
 * whether a human must approve. Payment is scheduled only by the approval
 * endpoint after that decision.
 */
@Service
class WorkflowDemoService(
    private val ai: InvoiceAnalysisService,
    private val approvalGateway: WorkflowHumanApprovalGateway,
    private val email: FakeEmailService,
    private val observer: WorkflowObserver,
    private val telemetry: GovernanceTelemetry,
) {
    private val workflow = workflow<InvoiceWorkflowState>(
        name = "invoice-approval-demo",
        definitionVersion = "2",
    ) {
        localStep("classify") { state, _ ->
            state.copy(document = state.request.toClassifiedDocument())
        }

        localStep("route") { state, _ ->
            state.copy(route = state.request.classification.toRoute())
        }

        aiStep(
            name = "assess",
            replayPolicy = ReplayPolicy.NON_REPLAYABLE,
            input = { state, _ -> state.analysisInput() },
            invoke = { input, _ ->
                telemetry.traceModelCall(input.document.classification, input.route) {
                    when (input.route) {
                        InvoiceRoute.CLOUD -> ai.analyzeCloud(input.document)
                        InvoiceRoute.LOCAL -> ai.preAssessLocal(input.document)
                        InvoiceRoute.LOCAL_NVIDIA -> ai.analyzeLocalNvidia(input.document)
                        InvoiceRoute.EU_CLOUD -> ai.analyzeEuNvidia(input.document)
                        InvoiceRoute.GLOBAL_CLOUD -> ai.analyzeGlobalNvidia(input.document)
                    }
                }
            },
            merge = { state, assessment, _ -> state.copy(assessment = assessment) },
        )

        localStep("request-human-approval") { state, context ->
            if (state.request.invoice.amountCents <= APPROVAL_THRESHOLD_CENTS) {
                state
            } else {
                state.copy(
                    approval = approvalGateway.requestPaymentApproval(
                        assessment = state.assessment ?: error("missing assessment"),
                        workflowRunId = context.workflowId,
                    ),
                )
            }
        }

        localStep("notify-approver") { state, _ ->
            val suspension = state.approval
                ?: return@localStep state
            email.sendApprovalRequest(
                to = "approver@ktconf.example",
                invoiceId = state.request.invoice.invoiceId,
                approvalId = suspension.approvalId.value,
            )
            state.copy(notificationStatus = "RECORDED")
        }

        localStep("finalize") { state, _ -> state.copy(outcome = state.toOutcome()) }
    }.build { it.outcome ?: error("workflow did not finalize") }

    suspend fun analyze(request: AnalyzeInvoiceRequest): WorkflowOutcome = workflow.run(
        initialState = InvoiceWorkflowState(request),
        context = WorkflowContext(workflowId = "invoice-${request.invoice.invoiceId}-${UUID.randomUUID()}"),
        observer = observer,
    )
}

data class InvoiceWorkflowState(
    val request: AnalyzeInvoiceRequest,
    val document: ClassifiedDocument<InvoiceDocument>? = null,
    val route: InvoiceRoute? = null,
    val assessment: InvoiceAssessment? = null,
    val approval: ApprovalRequestResult.Suspended? = null,
    val notificationStatus: String? = null,
    val outcome: WorkflowOutcome? = null,
)

private data class InvoiceAnalysisInput(
    val document: ClassifiedDocument<InvoiceDocument>,
    val route: InvoiceRoute,
)

private fun InvoiceWorkflowState.analysisInput(): InvoiceAnalysisInput = InvoiceAnalysisInput(
    document = document ?: error("missing classified document"),
    route = route ?: error("missing route"),
)

private fun InvoiceWorkflowState.toOutcome(): WorkflowOutcome {
    val typedAssessment = assessment ?: error("missing assessment")
    val selectedRoute = route ?: error("missing route")
    return when (val humanApproval = approval) {
        null -> WorkflowOutcome.Completed(WorkflowInvoiceResult(typedAssessment, selectedRoute))
        else -> WorkflowOutcome.AwaitingApproval(
            approvalId = humanApproval.approvalId.value,
            workflowRunId = humanApproval.workflowRunId.value,
            approvalGate = WorkflowHumanApprovalGateway.APPROVAL_GATE,
            assessment = typedAssessment,
            notificationStatus = notificationStatus ?: error("approver was not notified"),
        )
    }
}

private fun DataClassification.toRoute(): InvoiceRoute = when (this) {
    DataClassification.PUBLIC, DataClassification.INTERNAL -> InvoiceRoute.CLOUD
    DataClassification.CONFIDENTIAL, DataClassification.RESTRICTED -> InvoiceRoute.LOCAL
}

data class WorkflowInvoiceResult(
    val assessment: InvoiceAssessment,
    val selectedRoute: InvoiceRoute,
)

sealed interface WorkflowOutcome {
    data class Completed(val result: WorkflowInvoiceResult) : WorkflowOutcome

    data class AwaitingApproval(
        val approvalId: String,
        val workflowRunId: String,
        val approvalGate: String,
        val assessment: InvoiceAssessment,
        val notificationStatus: String,
    ) : WorkflowOutcome
}

private const val APPROVAL_THRESHOLD_CENTS = 500_000L
