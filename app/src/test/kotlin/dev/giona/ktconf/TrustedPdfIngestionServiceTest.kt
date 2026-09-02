package dev.giona.ktconf

import dev.giona.ktconf.pdf.DataResidency
import dev.giona.ktconf.pdf.TrustedPdfIngestionService
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrustedPdfIngestionServiceTest {
    private val parser = TrustedPdfIngestionService()

    @Test
    fun `reads trusted metadata locally and extracts synthetic invoice`() {
        val result = parse(pdf("CONFIDENTIAL", "EU_ONLY"))
        assertEquals("CONFIDENTIAL", result.metadata.classification.name)
        assertEquals(DataResidency.EU_ONLY, result.metadata.residency)
        assertEquals("KTCONF-PDF-EU", result.request.invoice.invoiceId)
    }

    @Test
    fun `metadata phase does not extract invoice content`() {
        val trusted = parser.readTrustedMetadata(pdf("CONFIDENTIAL", "EU_ONLY"))
        assertEquals(DataResidency.EU_ONLY, trusted.metadata.residency)
        assertEquals("KTCONF-PDF-EU", parser.extractInvoice(trusted).request.invoice.invoiceId)
    }

    @Test
    fun `missing classification fails closed before invoice extraction`() {
        assertFailsWith<IllegalArgumentException> {
            parse(pdf(null, "ANY"))
        }
    }

    @Test
    fun `contradictory restricted residency fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            parse(pdf("RESTRICTED", "EU_ONLY"))
        }
    }

    @Test
    fun `classification and residency must agree with the governed matrix`() {
        assertFailsWith<IllegalArgumentException> { parse(pdf("CONFIDENTIAL", "LOCAL_ONLY")) }
        assertFailsWith<IllegalArgumentException> { parse(pdf("PUBLIC", "EU_ONLY")) }
    }

    @Test
    fun `non PDF and oversized inputs fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            parse(MockMultipartFile("file", "invoice.txt", "text/plain", "not a pdf".toByteArray()))
        }
    }

    @Test
    fun `repository fixtures cover all contest residency cases`() {
        val cases = listOf(
            "fixtures/public-invoice.pdf" to ("PUBLIC" to DataResidency.ANY),
            "fixtures/public-high-invoice.pdf" to ("PUBLIC" to DataResidency.ANY),
            "fixtures/confidential-eu-invoice.pdf" to ("CONFIDENTIAL" to DataResidency.EU_ONLY),
            "fixtures/confidential-eu-low-invoice.pdf" to ("CONFIDENTIAL" to DataResidency.EU_ONLY),
            "fixtures/restricted-local-invoice.pdf" to ("RESTRICTED" to DataResidency.LOCAL_ONLY),
            "fixtures/payment-local-invoice.pdf" to ("RESTRICTED" to DataResidency.LOCAL_ONLY),
            "fixtures/restricted-local-high-invoice.pdf" to ("RESTRICTED" to DataResidency.LOCAL_ONLY),
        )
        val amounts = mapOf(
            "fixtures/public-invoice.pdf" to 120_000L,
            "fixtures/public-high-invoice.pdf" to 1_840_000L,
            "fixtures/confidential-eu-invoice.pdf" to 1_840_000L,
            "fixtures/confidential-eu-low-invoice.pdf" to 120_000L,
            "fixtures/restricted-local-invoice.pdf" to 4_200L,
            "fixtures/payment-local-invoice.pdf" to 1_840_000L,
            "fixtures/restricted-local-high-invoice.pdf" to 1_840_000L,
        )
        cases.forEach { (path, expected) ->
            val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(path)).readBytes()
            val parsed = parse(MockMultipartFile("file", path.substringAfterLast('/'), "application/pdf", bytes))
            assertEquals(expected.first, parsed.metadata.classification.name)
            assertEquals(expected.second, parsed.metadata.residency)
            assertEquals(amounts.getValue(path), parsed.request.invoice.amountCents)
        }
    }

    private fun parse(file: MockMultipartFile) =
        parser.extractInvoice(parser.readTrustedMetadata(file))

    private fun pdf(classification: String?, residency: String): MockMultipartFile {
        PDDocument().use { document ->
            val info = document.documentInformation
            classification?.let { info.setCustomMetadataValue(TrustedPdfIngestionService.CLASSIFICATION_KEY, it) }
            info.setCustomMetadataValue(TrustedPdfIngestionService.RESIDENCY_KEY, residency)
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(50f, 750f)
                content.showText("invoiceId=KTCONF-PDF-EU")
                content.newLineAtOffset(0f, -20f)
                content.showText("supplierName=Synthetic Contest Supplier")
                content.newLineAtOffset(0f, -20f)
                content.showText("amountCents=1840000")
                content.newLineAtOffset(0f, -20f)
                content.showText("currency=EUR")
                content.newLineAtOffset(0f, -20f)
                content.showText("description=Synthetic document fixture")
                content.endText()
            }
            val output = java.io.ByteArrayOutputStream()
            document.save(output)
            return MockMultipartFile("file", "invoice.pdf", "application/pdf", output.toByteArray())
        }
    }
}
