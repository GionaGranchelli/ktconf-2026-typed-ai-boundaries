package dev.giona.ktconf.workflowdemo

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.policy.DataClassification
import dev.tramai.orchestration.ReplayPolicy
import dev.tramai.orchestration.WorkflowContext
import dev.tramai.orchestration.WorkflowObserver
import dev.tramai.orchestration.workflow
import java.util.UUID
import org.springframework.stereotype.Service

/** Workflow-shaped version of the normal invoice analysis path. */
@Service
class WorkflowDemoService(
    private val ai: InvoiceAnalysisService,
    private val observer: WorkflowObserver,
    private val telemetry: GovernanceTelemetry,
) {
    private val workflow = workflow<InvoiceWorkflowState>("invoice-review-demo", definitionVersion = "1") {
        localStep("classify") { state, _ ->
            state.copy(document = state.request.toClassifiedDocument())
        }
        localStep("route") { state, _ ->
            state.copy(
                route = when (state.request.classification) {
                    DataClassification.PUBLIC, DataClassification.INTERNAL -> InvoiceRoute.CLOUD
                    DataClassification.CONFIDENTIAL, DataClassification.RESTRICTED -> InvoiceRoute.LOCAL
                },
            )
        }
        aiStep(
            name = "analyze",
            replayPolicy = ReplayPolicy.NON_REPLAYABLE,
            input = { state, _ ->
                InvoiceAnalysisInput(
                    document = state.document ?: error("missing classified document"),
                    route = state.route ?: error("missing route"),
                )
            },
            invoke = { input, _ ->
                telemetry.traceModelCall(input.document.classification, input.route) {
                    when (input.route) {
                        InvoiceRoute.CLOUD -> ai.analyzeCloud(input.document)
                        InvoiceRoute.LOCAL -> ai.analyzeLocal(input.document)
                    }
                }
            },
            merge = { state, assessment, _ -> state.copy(assessment = assessment) },
        )
        localStep("finalize") { state, _ ->
            state.copy(
                result = WorkflowInvoiceResult(
                    state.assessment ?: error("missing assessment"),
                    state.route ?: error("missing route"),
                ),
            )
        }
    }.build { it.result ?: error("workflow did not finalize") }

    suspend fun analyze(request: AnalyzeInvoiceRequest): WorkflowInvoiceResult = workflow.run(
        initialState = InvoiceWorkflowState(request),
        context = WorkflowContext(workflowId = "workflow-demo-${UUID.randomUUID()}"),
        observer = observer,
    )
}

data class InvoiceWorkflowState(
    val request: AnalyzeInvoiceRequest,
    val document: ClassifiedDocument<InvoiceDocument>? = null,
    val route: InvoiceRoute? = null,
    val assessment: InvoiceAssessment? = null,
    val result: WorkflowInvoiceResult? = null,
)

private data class InvoiceAnalysisInput(
    val document: ClassifiedDocument<InvoiceDocument>,
    val route: InvoiceRoute,
)

data class WorkflowInvoiceResult(val assessment: InvoiceAssessment, val selectedRoute: InvoiceRoute)
