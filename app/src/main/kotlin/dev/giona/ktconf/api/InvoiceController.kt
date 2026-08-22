package dev.giona.ktconf.api

import dev.giona.ktconf.application.AnalyzeOutcome
import dev.giona.ktconf.application.InvoiceApplicationService
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.domain.InvoiceDocument
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The typed boundary, exposed as an ordinary HTTP API.
 *
 * 200 → typed InvoiceAssessment
 * 202 → workflow suspended, awaiting approval (the HTTP request finished)
 * 403 → policy denial (RESTRICTED data on a cloud provider)
 * 422 → structured-output rejection (model produced untypable output)
 */
@RestController
@RequestMapping("/invoices")
class InvoiceController(
    private val app: InvoiceApplicationService,
) {

    @GetMapping
    fun list(): List<InvoiceDocument> =
        listOf(
            DemoInvoices.catering.payload,
            DemoInvoices.paymentInvoice.payload,
            DemoInvoices.restrictedAdvisory.payload,
        )

    @PostMapping("/analyze")
    suspend fun analyze(@RequestBody invoice: InvoiceDocument): ResponseEntity<Any> =
        when (val outcome = app.analyze(invoice)) {
            is AnalyzeOutcome.Typed -> ResponseEntity.ok(outcome.assessment)
            is AnalyzeOutcome.AwaitingApproval -> ResponseEntity.status(202).body(
                AwaitingApprovalResponse(
                    status = "AWAITING_APPROVAL",
                    approvalId = outcome.approvalId,
                    workflowRunId = outcome.workflowRunId,
                    toolName = outcome.toolName,
                ),
            )
        }
}

data class AwaitingApprovalResponse(
    val status: String,
    val approvalId: String,
    val workflowRunId: String,
    val toolName: String,
)
