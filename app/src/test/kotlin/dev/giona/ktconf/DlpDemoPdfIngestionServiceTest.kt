package dev.giona.ktconf

import dev.giona.ktconf.pdf.DataResidency
import dev.giona.ktconf.pdf.DlpDemoPdfIngestionService
import dev.giona.ktconf.pdf.InvalidTrustedPdfException
import dev.giona.ktconf.pdf.TrustedPdfIngestionService
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DlpDemoPdfIngestionServiceTest {
    private val service = DlpDemoPdfIngestionService(TrustedPdfIngestionService())

    @Test
    fun `fixture parses into the expected trusted DLP document`() {
        val parsed = service.readTrustedDocument(fixture())

        assertEquals("KTCONF-DLP-001", parsed.document.documentId)
        assertEquals("Example Conference Services BV", parsed.document.company)
        assertEquals("EUR 4,280.00", parsed.document.amount)
        assertEquals("Conference venue services", parsed.document.purpose)
        assertEquals("finance@example-confidential.eu", parsed.document.contactEmail)
        assertEquals("NL91ABNA0417164300", parsed.document.paymentIban)
        assertEquals("CONFIDENTIAL", parsed.metadata.classification.name)
        assertEquals(DataResidency.EU_ONLY, parsed.metadata.residency)
    }

    @Test
    fun `non DLP repository fixtures fail closed for this endpoint`() {
        val bytes = requireNotNull(
            javaClass.classLoader.getResourceAsStream("fixtures/confidential-eu-invoice.pdf"),
        ).readBytes()

        assertFailsWith<InvalidTrustedPdfException> {
            service.readTrustedDocument(
                MockMultipartFile("file", "confidential-eu-invoice.pdf", "application/pdf", bytes),
            )
        }
    }

    private fun fixture(): MockMultipartFile {
        val bytes = requireNotNull(
            javaClass.classLoader.getResourceAsStream("fixtures/dlp-confidential-document.pdf"),
        ).readBytes()
        return MockMultipartFile("file", "dlp-confidential-document.pdf", "application/pdf", bytes)
    }
}
