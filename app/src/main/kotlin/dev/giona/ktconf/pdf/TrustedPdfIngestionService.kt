package dev.giona.ktconf.pdf

import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.tramai.core.policy.DataClassification
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

enum class DataResidency { ANY, EU_ONLY, LOCAL_ONLY }

data class TrustedPdfMetadata(
    val classification: DataClassification,
    val residency: DataResidency,
)

data class PdfInvoice(
    val request: AnalyzeInvoiceRequest,
    val metadata: TrustedPdfMetadata,
)

/**
 * Parses the small contest PDF contract locally. Metadata is read before text
 * extraction, and the raw PDF is never logged or returned to the model layer.
 */
@Service
class TrustedPdfIngestionService {
    companion object {
        const val MAX_PDF_BYTES = 5 * 1024 * 1024
        const val CLASSIFICATION_KEY = "KTCONF-Classification"
        const val RESIDENCY_KEY = "KTCONF-Residency"
    }

    fun parse(file: MultipartFile): PdfInvoice {
        require(file.contentType == "application/pdf") { "PDF content type is required" }
        require(file.size in 1..MAX_PDF_BYTES) { "PDF size must be between 1 byte and 5 MiB" }
        val bytes = file.bytes
        require(bytes.size <= MAX_PDF_BYTES) { "PDF exceeds the 5 MiB limit" }

        try {
            Loader.loadPDF(bytes).use { document ->
                val metadata = parseMetadata(document.documentInformation, bytes)
                val text = PDFTextStripper().getText(document)
                return PdfInvoice(parseInvoice(text, metadata), metadata)
            }
        } catch (e: IOException) {
            throw IllegalArgumentException("Malformed or unsupported PDF", e)
        }
    }

    private fun parseMetadata(
        info: org.apache.pdfbox.pdmodel.PDDocumentInformation,
        bytes: ByteArray,
    ): TrustedPdfMetadata {
        val rawPdf = bytes.toString(StandardCharsets.ISO_8859_1)
        fun value(key: String): String? = info.getCustomMetadataValue(key)
            ?: Regex("/${Regex.escape(key)}\\s*\\(([^)]*)\\)").find(rawPdf)?.groupValues?.get(1)
        val classification = parseEnum(CLASSIFICATION_KEY, value(CLASSIFICATION_KEY)) { value ->
            DataClassification.valueOf(value)
        }
        val residency = parseEnum(RESIDENCY_KEY, value(RESIDENCY_KEY)) { value ->
            DataResidency.valueOf(value)
        }
        when (classification) {
            DataClassification.RESTRICTED -> require(residency == DataResidency.LOCAL_ONLY) {
                "RESTRICTED documents require LOCAL_ONLY residency"
            }
            DataClassification.CONFIDENTIAL -> require(
                residency == DataResidency.EU_ONLY || residency == DataResidency.LOCAL_ONLY,
            ) { "CONFIDENTIAL documents require EU_ONLY or LOCAL_ONLY residency" }
            DataClassification.PUBLIC, DataClassification.INTERNAL -> Unit
        }
        return TrustedPdfMetadata(classification, residency)
    }

    private fun <T> parseEnum(key: String, raw: String?, parser: (String) -> T): T {
        require(!raw.isNullOrBlank()) { "Missing trusted PDF metadata: $key" }
        return try {
            parser(raw.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported trusted PDF metadata: $key")
        }
    }

    private fun parseInvoice(text: String, metadata: TrustedPdfMetadata): AnalyzeInvoiceRequest {
        val fields = text.lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim() to it[1].trim() }
        fun required(name: String) = fields[name]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("PDF invoice field is missing: $name")
        val amount = required("amountCents").toLongOrNull()
            ?: throw IllegalArgumentException("PDF invoice amountCents is malformed")
        require(amount >= 0) { "PDF invoice amountCents must not be negative" }
        return AnalyzeInvoiceRequest(
            classification = metadata.classification,
            invoice = dev.giona.ktconf.domain.InvoiceDocument(
                invoiceId = required("invoiceId"),
                supplierName = required("supplierName"),
                amountCents = amount,
                currency = required("currency"),
                description = required("description"),
            ),
        )
    }
}
