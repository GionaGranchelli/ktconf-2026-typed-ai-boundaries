package dev.giona.ktconf.api

import dev.giona.ktconf.application.AnalyzeOutcome
import dev.giona.ktconf.application.AnalyzeInvoiceRequest
import dev.giona.ktconf.application.InvoiceService
import dev.giona.ktconf.domain.InvoiceAssessment
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The typed boundary, exposed as an ordinary HTTP API.
 *
 * 200 → typed InvoiceAssessment + visible routing metadata
 * 202 → workflow suspended, awaiting approval (the HTTP request finished)
 * 403 → policy denial (RESTRICTED data on a cloud provider)
 * 422 → structured-output rejection (model produced untypable output)
 */
@RestController
@RequestMapping("/invoices")
class InvoiceController(
    private val app: InvoiceService,
) {

    /** Normal route: the application selects local for RESTRICTED, cloud otherwise. */
    @PostMapping("/analyze")
    suspend fun analyze(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        when (val outcome = app.analyze(request)) {
            is AnalyzeOutcome.Typed -> ResponseEntity.ok(
                AnalyzeResponse(
                    assessment = outcome.assessment,
                    selectedModel = outcome.selectedModel,
                    selectedProviderZone = outcome.selectedProviderZone,
                ),
            )

            is AnalyzeOutcome.AwaitingApproval -> ResponseEntity.status(202).body(
                AwaitingApprovalResponse(
                    status = "AWAITING_APPROVAL",
                    approvalId = outcome.approvalId,
                    workflowRunId = outcome.workflowRunId,
                    toolName = outcome.toolName,
                ),
            )
        }

    /**
     * DEMO-ONLY intentional policy-violation proof: a RESTRICTED document
     * forced through the cloud operation. Same service, same runtime, same
     * operation as normal routing. TramAI denies before provider invocation
     * (403, reasonCode `classification-routing-blocked`). This is fault
     * injection for the stage, NOT production routing logic.
     */
    @PostMapping("/boundary/restricted-cloud")
    suspend fun restrictedCloud(@RequestBody request: AnalyzeInvoiceRequest): InvoiceAssessment =
        app.analyzeRestrictedViaCloud(request)
}

/** 200 envelope: the typed result plus the route the application chose. */
data class AnalyzeResponse(
    val assessment: InvoiceAssessment,
    val selectedModel: String,
    val selectedProviderZone: String,
)

data class AwaitingApprovalResponse(
    val status: String,
    val approvalId: String,
    val workflowRunId: String,
    val toolName: String,
)
