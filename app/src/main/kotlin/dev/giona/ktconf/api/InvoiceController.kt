package dev.giona.ktconf.api

import dev.giona.ktconf.application.AnalyzeOutcome
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.application.InvoiceService
import dev.giona.ktconf.application.DocumentHistoryService
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.pdf.TrustedPdfIngestionService
import dev.giona.ktconf.pdf.DataResidency
import dev.giona.ktconf.pdf.TrustedPdfMetadata
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.exception.PolicyViolationException
import org.springframework.http.ResponseEntity
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

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
    private val history: DocumentHistoryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Normal route: exhaustive when — PUBLIC/INTERNAL → cloud, CONFIDENTIAL/RESTRICTED → local. */
    @PostMapping("/analyze")
    suspend fun analyze(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> {
        log.info("Invoice analysis requested: invoiceId={}, classification={}", request.invoice.invoiceId, request.classification)
        return app.analyze(request).toHttpResponse()
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

    /** Opt-in hosted NVIDIA route; high-risk requests use the governed payment flow. */
    @PostMapping("/global-nvidia")
    suspend fun globalNvidia(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        app.analyze(request, InvoiceRoute.GLOBAL_CLOUD).toHttpResponse()

    /** Opt-in task-003 typed-inference route for the configured local NVIDIA model. */
    @PostMapping("/local-nvidia")
    suspend fun localNvidia(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        ResponseEntity.ok(AnalyzeResponse(app.analyzeLocalNvidia(request), InvoiceRoute.LOCAL_NVIDIA, ClassificationSource.DECLARED))

    /** Contest payment proof: local NVIDIA assessment plus TramAI approval gate. */
    @PostMapping("/analyze/local-nvidia")
    suspend fun analyzeLocalNvidiaPayment(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        app.analyze(request, InvoiceRoute.LOCAL_NVIDIA).toHttpResponse()

    /** Opt-in EU route; high-risk requests use the governed payment flow too. */
    @PostMapping("/eu-scaleway")
    suspend fun euScaleway(@RequestBody request: AnalyzeInvoiceRequest): ResponseEntity<Any> =
        app.analyze(request, InvoiceRoute.EU_CLOUD).toHttpResponse()

    /** Contest PDF entrypoint: metadata phase precedes local content preparation. */
    @PostMapping("/analyze-pdf", consumes = ["multipart/form-data"])
    suspend fun analyzePdf(
        @RequestPart("file") file: org.springframework.web.multipart.MultipartFile,
        @RequestParam("forceRoute", required = false) forceRoute: InvoiceRoute?,
    ): ResponseEntity<Any> {
        val trusted = pdfIngestion.readTrustedMetadata(file)
        // The trusted metadata phase precedes local content preparation. The
        // governed operation below is where TramAI authorizes placement.
        val proposedRoute = forceRoute ?: trusted.metadata.proposedRoute()
        val parsed = pdfIngestion.extractInvoice(trusted)
        val outcome = try {
            app.analyze(parsed.request, proposedRoute, ClassificationSource.RULE_BASED)
        } catch (error: PolicyViolationException) {
            history.recordRejected(parsed.request.invoice, parsed.metadata, proposedRoute, error)
            throw error
        }
        history.record(parsed.request.invoice, parsed.metadata, outcome)
        return when (outcome) {
            is AnalyzeOutcome.Typed -> ResponseEntity.ok(
                PdfAnalyzeResponse(parsed.metadata, outcome.assessment, outcome.selectedRoute, outcome.classificationSource),
            )
            is AnalyzeOutcome.AwaitingApproval -> ResponseEntity.status(202).body(
                PdfAwaitingApprovalResponse(
                    metadata = parsed.metadata,
                    invoice = parsed.request.invoice,
                    selectedRoute = outcome.selectedRoute,
                    approvalId = outcome.approvalId,
                    workflowRunId = outcome.workflowRunId,
                    toolName = outcome.toolName,
                    rationale = outcome.rationale,
                    classificationSource = outcome.classificationSource,
                    approvalExpiresAt = outcome.approvalExpiresAt,
                    notificationStatus = outcome.notificationStatus,
                    notificationRecipient = outcome.notificationRecipient,
                    notificationSubject = outcome.notificationSubject,
                ),
            )
        }
    }
}

private fun AnalyzeOutcome.toHttpResponse(): ResponseEntity<Any> = when (this) {
    is AnalyzeOutcome.Typed -> ResponseEntity.ok(
        AnalyzeResponse(assessment, selectedRoute, classificationSource),
    )
    is AnalyzeOutcome.AwaitingApproval -> ResponseEntity.status(202).body(
        AwaitingApprovalResponse(
            status = "AWAITING_APPROVAL",
            approvalId = approvalId,
            workflowRunId = workflowRunId,
            toolName = toolName,
            rationale = rationale,
            classificationSource = classificationSource,
            approvalExpiresAt = approvalExpiresAt,
            notificationStatus = notificationStatus,
            notificationRecipient = notificationRecipient,
            notificationSubject = notificationSubject,
        ),
    )
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
    val classificationSource: ClassificationSource,
)

data class AwaitingApprovalResponse(
    val status: String,
    val approvalId: String,
    val workflowRunId: String,
    val toolName: String,
    val rationale: String,
    val classificationSource: ClassificationSource,
    val approvalExpiresAt: Instant? = null,
    val notificationStatus: String? = null,
    val notificationRecipient: String? = null,
    val notificationSubject: String? = null,
)

data class PdfAnalyzeResponse(
    val metadata: dev.giona.ktconf.pdf.TrustedPdfMetadata,
    val assessment: InvoiceAssessment,
    val selectedRoute: InvoiceRoute,
    val classificationSource: ClassificationSource,
)

data class PdfAwaitingApprovalResponse(
    val metadata: TrustedPdfMetadata,
    val invoice: dev.giona.ktconf.domain.InvoiceDocument,
    val selectedRoute: InvoiceRoute,
    val status: String = "AWAITING_APPROVAL",
    val approvalId: String,
    val workflowRunId: String,
    val toolName: String,
    val rationale: String,
    val classificationSource: ClassificationSource,
    val approvalExpiresAt: Instant,
    val notificationStatus: String,
    val notificationRecipient: String,
    val notificationSubject: String,
)
