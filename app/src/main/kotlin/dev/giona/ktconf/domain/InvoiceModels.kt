package dev.giona.ktconf.domain

import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.core.annotations.AiRange
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification

/**
 * A raw invoice document submitted for analysis.
 * The classification lives on the request/ClassifiedDocument wrapper,
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
enum class InvoiceAction { REVIEW_ONLY, REQUEST_HUMAN_APPROVAL, SCHEDULE_PAYMENT }

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
    @property:AiRange(min = 0.0, max = 1.0)
    val confidence: Double,
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

/**
 * The demo request shape: classification is an explicit governance fact
 * supplied by the caller. In production it could come from upstream
 * metadata, DLP, a deterministic classifier, a policy engine, or explicit
 * workflow state — TramAI never infers confidentiality from the payload.
 *
 * NOTE: in this demo the request's classification represents a TRUSTED
 * UPSTREAM GOVERNANCE FACT. Nothing stops an arbitrary external caller
 * from saying PUBLIC; the demo assumes the classification was already
 * decided by a trusted component. The boundary proof (RESTRICTED → cloud)
 * shows what TramAI does with a wrong route once the classification is set.
 */
data class AnalyzeInvoiceRequest(
    val classification: DataClassification,
    val invoice: InvoiceDocument,
)

/** Wraps a request into TramAI's classification envelope. */
fun AnalyzeInvoiceRequest.toClassifiedDocument(
    source: ClassificationSource = ClassificationSource.DECLARED,
): ClassifiedDocument<InvoiceDocument> =
    ClassifiedDocument(
        payload = invoice,
        classification = classification,
        source = source,
    )
