package dev.giona.ktconf

import dev.giona.ktconf.application.AnalyzeOutcome
import dev.giona.ktconf.application.DocumentHistoryService
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.pdf.DataResidency
import dev.giona.ktconf.pdf.TrustedPdfMetadata
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentHistoryServiceTest {
    @Test
    fun `low risk auto payment is recorded as scheduled exactly once`() {
        val invoice = InvoiceDocument("PUBLIC-LOW", "Synthetic", 120_000, "EUR", "Synthetic")
        val record = DocumentHistoryService().record(
            invoice = invoice,
            metadata = TrustedPdfMetadata(DataClassification.PUBLIC, DataResidency.ANY),
            outcome = AnalyzeOutcome.Typed(
                assessment = InvoiceAssessment(
                    invoiceId = invoice.invoiceId,
                    supplierName = invoice.supplierName,
                    amountCents = invoice.amountCents,
                    currency = invoice.currency,
                    risk = InvoiceRisk.LOW,
                    recommendedAction = InvoiceAction.SCHEDULE_PAYMENT,
                    confidence = 0.9,
                    rationale = "Payment scheduled automatically",
                ),
                selectedRoute = InvoiceRoute.GLOBAL_CLOUD,
                classificationSource = ClassificationSource.RULE_BASED,
                paymentScheduled = true,
            ),
        )

        assertEquals("SCHEDULED", record.status)
        assertEquals("PAYMENT_AUTO_SCHEDULED", record.workflowEvents.last().type)
    }

    @Test
    fun `high risk typed analysis requires review and is never auto approved`() {
        val invoice = InvoiceDocument("EU-HIGH", "Synthetic", 1_800_000, "EUR", "Synthetic")
        val record = DocumentHistoryService().record(
            invoice = invoice,
            metadata = TrustedPdfMetadata(DataClassification.CONFIDENTIAL, DataResidency.EU_ONLY),
            outcome = AnalyzeOutcome.Typed(
                assessment = InvoiceAssessment(
                    invoiceId = invoice.invoiceId,
                    supplierName = invoice.supplierName,
                    amountCents = invoice.amountCents,
                    currency = invoice.currency,
                    risk = InvoiceRisk.HIGH,
                    recommendedAction = InvoiceAction.REQUEST_HUMAN_APPROVAL,
                    confidence = 0.9,
                    rationale = "Synthetic",
                ),
                selectedRoute = InvoiceRoute.EU_CLOUD,
                classificationSource = ClassificationSource.RULE_BASED,
            ),
        )

        assertEquals("REVIEW_REQUIRED", record.status)
        assertEquals("HUMAN_REVIEW_REQUIRED", record.workflowEvents.last().type)
    }
}
