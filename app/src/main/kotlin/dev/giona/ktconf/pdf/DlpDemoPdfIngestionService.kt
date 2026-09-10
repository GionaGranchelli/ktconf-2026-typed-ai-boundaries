package dev.giona.ktconf.pdf

import dev.giona.ktconf.domain.ConfidentialDocument
import dev.tramai.core.policy.DataClassification
import java.io.IOException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

data class DlpTrustedDocument(
    val document: ConfidentialDocument,
    val metadata: TrustedPdfMetadata,
)

@Service
class DlpDemoPdfIngestionService(
    private val trustedPdfIngestionService: TrustedPdfIngestionService,
) {

    fun readTrustedDocument(file: MultipartFile): DlpTrustedDocument {
        val trusted = trustedPdfIngestionService.readTrustedMetadata(file)
        if (trusted.metadata.classification != DataClassification.CONFIDENTIAL) {
            throw InvalidTrustedPdfException("DLP demo requires trusted CONFIDENTIAL classification")
        }
        if (trusted.metadata.residency != DataResidency.EU_ONLY) {
            throw InvalidTrustedPdfException("DLP demo requires trusted EU_ONLY residency")
        }

        try {
            Loader.loadPDF(trusted.bytes).use { document ->
                val text = PDFTextStripper().getText(document)
                return DlpTrustedDocument(
                    document = parseDocument(text),
                    metadata = trusted.metadata,
                )
            }
        } catch (error: IOException) {
            throw InvalidTrustedPdfException("Malformed or unsupported PDF", error)
        }
    }

    private fun parseDocument(text: String): ConfidentialDocument {
        val lines = text.lineSequence()
            .map(String::trim)
            .toList()

        fun lineValue(prefix: String): String = lines.firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw InvalidTrustedPdfException("DLP document field is missing: $prefix")

        fun blockValue(heading: String): String {
            val start = lines.indexOfFirst { it == heading }
            if (start == -1) {
                throw InvalidTrustedPdfException("DLP document field is missing: $heading")
            }
            return lines.drop(start + 1)
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw InvalidTrustedPdfException("DLP document field is missing: $heading")
        }

        val document = ConfidentialDocument(
            documentId = lineValue("DOCUMENT ID:"),
            company = lineValue("Company:"),
            amount = lineValue("Amount:"),
            purpose = lineValue("Purpose:"),
            paymentIban = blockValue("Payment IBAN:"),
            contactEmail = blockValue("Contact:"),
            notes = blockValue("Notes:"),
        )
        if (document.documentId != "KTCONF-DLP-001") {
            throw InvalidTrustedPdfException("Unsupported DLP demo document: ${document.documentId}")
        }
        return document
    }
}
