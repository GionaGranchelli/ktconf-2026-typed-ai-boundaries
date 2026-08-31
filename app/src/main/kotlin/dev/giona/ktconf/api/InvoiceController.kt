package dev.giona.ktconf.api

import dev.giona.ktconf.application.AnalyzeOutcome
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.application.InvoiceService
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.pdf.TrustedPdfIngestionService
import dev.giona.ktconf.pdf.DataResidency
import dev.giona.ktconf.pdf.TrustedPdfMetadata
import org.springframework.http.ResponseEntity
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
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
    private val pdfIngestion: TrustedPdfIngestionService,
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
                ResponseEntity.status(202).body(
                    AwaitingApprovalResponse(
                        status = "AWAITING_APPROVAL",
                        approvalId = outcome.approvalId,
                        workflowRunId = outcome.workflowRunId,
                        toolName = outcome.toolName,
                        rationale = outcome.rationale,
                    ),
                )
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

    /** DEMO-ONLY intentional policy-violation proof: force RESTRICTED to EU. */
    @PostMapping("/boundary/restricted-eu")
    suspend fun restrictedEu(@RequestBody request: AnalyzeInvoiceRequest): InvoiceAssessment {
        log.warn("Boundary proof requested: invoiceId={} forced to EU with classification={}", request.invoice.invoiceId, request.classification)
        return app.analyzeRestrictedViaEu(request)
    }

    /** DEMO-ONLY hero proof: force the EU-confidential PDF to GLOBAL_CLOUD. */
    @PostMapping("/boundary/confidential-eu-global", consumes = ["multipart/form-data"])
    suspend fun confidentialEuGlobal(@RequestPart("file") file: org.springframework.web.multipart.MultipartFile): InvoiceAssessment {
        val trusted = pdfIngestion.readTrustedMetadata(file)
        require(trusted.metadata.classification == dev.tramai.core.policy.DataClassification.CONFIDENTIAL)
        require(trusted.metadata.residency == DataResidency.EU_ONLY)
        val parsed = pdfIngestion.extractInvoice(trusted)
        log.warn("Boundary proof requested: confidential EU invoice={} forced to GLOBAL_CLOUD", parsed.request.invoice.invoiceId)
        return app.analyze(parsed.request, InvoiceRoute.GLOBAL_CLOUD).let { outcome ->
            when (outcome) {
                is AnalyzeOutcome.Typed -> outcome.assessment
                is AnalyzeOutcome.AwaitingApproval -> error("unexpected approval for forced global proof")
            }
        }
    }

    /** Opt-in task-002 proof route for hosted NVIDIA Nemotron. */
    @PostMapping("/global-nvidia")
    suspend fun globalNvidia(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        ResponseEntity.ok(AnalyzeResponse(app.analyzeGlobalNvidia(request), InvoiceRoute.GLOBAL_CLOUD))

    /** Opt-in task-003 proof route for local NVIDIA Nemotron. */
    @PostMapping("/local-nvidia")
    suspend fun localNvidia(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        ResponseEntity.ok(AnalyzeResponse(app.analyzeLocalNvidia(request), InvoiceRoute.LOCAL_NVIDIA))

    /** Contest payment proof: local NVIDIA assessment plus TramAI approval gate. */
    @PostMapping("/analyze/local-nvidia")
    suspend fun analyzeLocalNvidiaPayment(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        when (val outcome = app.analyze(request, InvoiceRoute.LOCAL_NVIDIA)) {
            is AnalyzeOutcome.Typed -> ResponseEntity.ok(AnalyzeResponse(outcome.assessment, outcome.selectedRoute))
            is AnalyzeOutcome.AwaitingApproval -> ResponseEntity.status(202).body(
                AwaitingApprovalResponse(
                    status = "AWAITING_APPROVAL",
                    approvalId = outcome.approvalId,
                    workflowRunId = outcome.workflowRunId,
                    toolName = outcome.toolName,
                    rationale = outcome.rationale,
                ),
            )
        }

    /** Opt-in task-004 proof route for the configured EU managed endpoint. */
    @PostMapping("/eu-scaleway")
    suspend fun euScaleway(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        ResponseEntity.ok(AnalyzeResponse(app.analyzeEuScaleway(request), InvoiceRoute.EU_CLOUD))

    /** Contest PDF entrypoint: metadata phase precedes local content preparation. */
    @PostMapping("/analyze-pdf", consumes = ["multipart/form-data"])
    suspend fun analyzePdf(@RequestPart("file") file: org.springframework.web.multipart.MultipartFile): ResponseEntity<Any> {
        val trusted = pdfIngestion.readTrustedMetadata(file)
        // The trusted metadata phase precedes local content preparation. The
        // governed operation below is where TramAI authorizes placement.
        val proposedRoute = trusted.metadata.proposedRoute()
        val parsed = pdfIngestion.extractInvoice(trusted)
        return when (val outcome = app.analyze(parsed.request, proposedRoute)) {
            is AnalyzeOutcome.Typed -> ResponseEntity.ok(
                PdfAnalyzeResponse(parsed.metadata, outcome.assessment, outcome.selectedRoute),
            )
            is AnalyzeOutcome.AwaitingApproval -> ResponseEntity.status(202).body(
                PdfAwaitingApprovalResponse(
                    metadata = parsed.metadata,
                    selectedRoute = outcome.selectedRoute,
                    approvalId = outcome.approvalId,
                    workflowRunId = outcome.workflowRunId,
                    toolName = outcome.toolName,
                    rationale = outcome.rationale,
                ),
            )
        }
    }
}

/** Metadata-driven proposal; TramAI still authorizes the selected operation. */
private fun TrustedPdfMetadata.proposedRoute(): InvoiceRoute = when (residency) {
    DataResidency.ANY -> InvoiceRoute.GLOBAL_CLOUD
    DataResidency.EU_ONLY -> InvoiceRoute.EU_CLOUD
    DataResidency.LOCAL_ONLY -> InvoiceRoute.LOCAL_NVIDIA
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
    val rationale: String,
)

data class PdfAnalyzeResponse(
    val metadata: dev.giona.ktconf.pdf.TrustedPdfMetadata,
    val assessment: InvoiceAssessment,
    val selectedRoute: InvoiceRoute,
)

data class PdfAwaitingApprovalResponse(
    val metadata: TrustedPdfMetadata,
    val selectedRoute: InvoiceRoute,
    val status: String = "AWAITING_APPROVAL",
    val approvalId: String,
    val workflowRunId: String,
    val toolName: String,
    val rationale: String,
)
