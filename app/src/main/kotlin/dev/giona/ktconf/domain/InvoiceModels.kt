package dev.giona.ktconf.domain

import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification

/**
 * A raw invoice document submitted for analysis.
 * The classification lives on the [ClassifiedDocument] wrapper,
 * not on the payload — the boundary is explicit.
 */
data class InvoiceDocument(
    val invoiceId: String,
    val supplierName: String,
    val amountCents: Long,
    val currency: String,
    val description: String,
)

/** Risk classification for an assessed invoice. */
enum class InvoiceRisk { LOW, HIGH }

/** Action recommended for an assessed invoice. */
enum class InvoiceAction { REVIEW_ONLY, SCHEDULE_PAYMENT }

/**
 * Typed result of the AI analysis.
 *
 * The application never parses model JSON — this type is produced by
 * TramAI's structured-output engine from the provider response.
 */
data class InvoiceAssessment(
    val invoiceId: String,
    val supplierName: String,
    val amountCents: Long,
    val currency: String,
    val risk: InvoiceRisk,
    val recommendedAction: InvoiceAction,
    val rationale: String,
)

/** Input for the schedule-payment tool. */
data class SchedulePaymentInput(
    val invoiceId: String,
    val amountCents: Long,
    val currency: String,
)

/** Result of the schedule-payment tool. */
data class SchedulePaymentResult(
    val paymentReference: String,
    val status: String,
)

/** Deterministic demo fixtures — the exact invoices used on stage. */
object DemoInvoices {
    /** Scenario 1/2 (+ typed --real): ordinary catering invoice, classified RESTRICTED. */
    val catering: ClassifiedDocument<InvoiceDocument> = ClassifiedDocument(
        payload = InvoiceDocument(
            invoiceId = "KTCONF-001",
            supplierName = "KTConf Catering BV",
            amountCents = 42_830,
            currency = "EUR",
            description = "Conference catering services",
        ),
        classification = DataClassification.RESTRICTED,
        source = ClassificationSource.DECLARED,
    )

    /** Scenario 3: high-value confidential advisory invoice, classified RESTRICTED. */
    val restrictedAdvisory: ClassifiedDocument<InvoiceDocument> = ClassifiedDocument(
        payload = InvoiceDocument(
            invoiceId = "KTCONF-RESTRICTED-001",
            supplierName = "ACME Acquisition Advisory",
            amountCents = 8_250_000,
            currency = "EUR",
            description = "Project MERGER-2026 confidential advisory services",
        ),
        classification = DataClassification.RESTRICTED,
        source = ClassificationSource.DECLARED,
    )

    /** Scenario 4/5: payment invoice — HIGH risk, requires payment scheduling. */
    val paymentInvoice: ClassifiedDocument<InvoiceDocument> = ClassifiedDocument(
        payload = InvoiceDocument(
            invoiceId = "KTCONF-PAY-001",
            supplierName = "KTConf AV & Stage Services BV",
            amountCents = 1_840_000,
            currency = "EUR",
            description = "Conference stage and AV production services",
        ),
        classification = DataClassification.RESTRICTED,
        source = ClassificationSource.DECLARED,
    )
}
