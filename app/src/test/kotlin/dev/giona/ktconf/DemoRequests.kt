package dev.giona.ktconf

import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.policy.DataClassification

/** Shared request fixtures for the deterministic oracle. */
object DemoRequests {

    fun request(
        classification: DataClassification,
        invoiceId: String,
        supplier: String,
        cents: Long,
        description: String,
    ): AnalyzeInvoiceRequest = AnalyzeInvoiceRequest(
        classification = classification,
        invoice = InvoiceDocument(
            invoiceId = invoiceId,
            supplierName = supplier,
            amountCents = cents,
            currency = "EUR",
            description = description,
        ),
    )

    /** PUBLIC KTCONF-001 → normal cloud route. */
    fun typed(): AnalyzeInvoiceRequest =
        request(DataClassification.PUBLIC, "KTCONF-001", "KTConf Catering BV", 42_830, "Catering")

    /** RESTRICTED KTCONF-001 → normal local route. */
    fun restricted(): AnalyzeInvoiceRequest =
        request(DataClassification.RESTRICTED, "KTCONF-001", "KTConf Catering BV", 42_830, "Catering")

    /** PUBLIC KTCONF-INVALID-001 → cloud route with deliberately invalid output. */
    fun invalid(): AnalyzeInvoiceRequest =
        request(DataClassification.PUBLIC, "KTCONF-INVALID-001", "KTConf", 42_830, "Broken")

    /** RESTRICTED KTCONF-PAY-001 → local route → schedule-payment → approval. */
    fun payment(): AnalyzeInvoiceRequest =
        request(DataClassification.RESTRICTED, "KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage")

    /** RESTRICTED KTCONF-PAY-002 → a second, distinct payment workflow. */
    fun payment2(): AnalyzeInvoiceRequest =
        request(DataClassification.RESTRICTED, "KTCONF-PAY-002", "KTConf AV & Stage Services BV", 950_000, "Stage")
}

/** HTTP view of the deny outcome (mirrors the service DTO shape). */
data class DenyView(
    val approvalId: String,
    val status: String,
)

/** HTTP view of the evidence response. */
data class EvidenceView(
    val auditEvents: List<Map<String, Any?>>,
    val chainValid: Boolean,
    val eventCount: Int,
    val evidenceDirectory: String,
)
