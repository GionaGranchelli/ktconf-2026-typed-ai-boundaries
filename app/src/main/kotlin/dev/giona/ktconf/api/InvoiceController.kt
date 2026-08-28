package dev.giona.ktconf.api

import dev.giona.ktconf.application.AnalyzeOutcome
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.application.InvoiceService
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import org.springframework.http.ResponseEntity
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    /** Normal route: exhaustive when — PUBLIC/INTERNAL → cloud, CONFIDENTIAL/RESTRICTED → local. */
    @PostMapping("/analyze")
    suspend fun analyze(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> {
        log.info("Invoice analysis requested: invoiceId={}, classification={}", request.invoice.invoiceId, request.classification)
        return when (val outcome = app.analyze(request)) {
            is AnalyzeOutcome.Typed -> {
                log.info("Invoice analysis completed: invoiceId={}, route={}, action={}, risk={}", request.invoice.invoiceId, outcome.selectedRoute, outcome.assessment.recommendedAction, outcome.assessment.risk)
                ResponseEntity.ok(AnalyzeResponse(outcome.assessment, outcome.selectedRoute))
            }

            is AnalyzeOutcome.AwaitingApproval -> {
                log.info("Invoice analysis suspended for approval: invoiceId={}, approvalId={}, tool={}", request.invoice.invoiceId, outcome.approvalId, outcome.toolName)
                ResponseEntity.status(202).body(AwaitingApprovalResponse("AWAITING_APPROVAL", outcome.approvalId, outcome.workflowRunId, outcome.toolName))
            }
        }
    }

    /**
     * DEMO-ONLY intentional policy-violation proof: a RESTRICTED document
     * forced through the cloud operation. Same service, same runtime, same
     * operation as normal routing. TramAI denies before provider invocation
     * (403, reasonCode `classification-routing-blocked`). This is fault
     * injection for the stage, NOT production routing logic.
     */
    @PostMapping("/boundary/restricted-cloud")
    suspend fun restrictedCloud(@RequestBody request: AnalyzeInvoiceRequest): InvoiceAssessment {
        log.warn("Boundary proof requested: invoiceId={} forced to cloud with classification={}", request.invoice.invoiceId, request.classification)
        return app.analyzeRestrictedViaCloud(request)
    }
}

/** 200 envelope: the typed result plus the route the application chose. */
data class AnalyzeResponse(
    val assessment: InvoiceAssessment,
    val selectedRoute: InvoiceRoute,
)

data class AwaitingApprovalResponse(
    val status: String,
    val approvalId: String,
    val workflowRunId: String,
    val toolName: String,
)
