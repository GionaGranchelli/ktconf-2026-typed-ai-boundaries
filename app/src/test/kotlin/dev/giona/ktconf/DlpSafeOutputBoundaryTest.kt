package dev.giona.ktconf

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.giona.ktconf.observability.DlpRedactionEvidenceStore
import dev.tramai.security.audit.AuditStore
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ktconf.providers.local.base-url=",
        "ktconf.providers.local.api-key=",
        "ktconf.providers.local-nvidia.base-url=",
        "ktconf.providers.local-nvidia.api-key=",
        "ktconf.providers.cloud.api-key=",
        "ktconf.providers.global-nvidia.api-key=",
        "ktconf.providers.eu-scaleway.base-url=",
        "ktconf.providers.eu-scaleway.api-key=",
    ],
)
@AutoConfigureMockMvc
@Import(DlpSafeOutputBoundaryTest.TestTelemetryConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DlpSafeOutputBoundaryTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var spans: InMemorySpanExporter

    @Autowired
    lateinit var dlpEvidence: DlpRedactionEvidenceStore

    @Autowired
    lateinit var auditStore: AuditStore

    @Test
    fun `HTTP result exposes only sanitized values and valid DLP metadata`() {
        val response = analyze()
        val body = response.toPrettyString()

        assertEquals("KTCONF-DLP-001", response["document"]["documentId"].asText())
        assertEquals("CONFIDENTIAL", response["metadata"]["classification"].asText())
        assertEquals("EU_ONLY", response["metadata"]["residency"].asText())
        assertEquals("EU_CLOUD", response["selectedRoute"].asText())
        assertEquals("analyzeDocument", response["operation"].asText())
        assertEquals("[EMAIL_REDACTED]", response["analysis"]["contactEmail"].asText())
        assertEquals("[IBAN_REDACTED]", response["analysis"]["paymentIban"].asText())
        assertEquals(2, response["dlp"]["replacementCount"].asInt())
        assertEquals(listOf("email", "iban"), response["dlp"]["ruleIds"].map { it.asText() })
        assertFalse(body.contains("finance@example-confidential.eu"))
        assertFalse(body.contains("NL91ABNA0417164300"))
    }

    @Test
    fun `audit evidence records safe rule level redactions only`() {
        analyze()

        val summary = dlpEvidence.summarySince(0, "analyzeDocument")
        val events = runBlocking { auditStore.readStream(summary.correlationId) }
        val dlpEvents = events.filter { it.enforcementPoint == "DLP_MODEL_OUTPUT" }

        assertEquals(2, dlpEvents.size)
        assertEquals(listOf("email", "iban"), dlpEvents.map { it.metadata["ruleId"] })
        assertEquals(listOf("1", "1"), dlpEvents.map { it.metadata["replacementCount"] })
        assertTrue(dlpEvents.all { it.decision == "REDACTED" })
        val serialized = dlpEvents.joinToString("\n") { "${it.reasonCode} ${it.metadata}" }
        assertFalse(serialized.contains("finance@example-confidential.eu"))
        assertFalse(serialized.contains("NL91ABNA0417164300"))
    }

    @Test
    fun `observability and logs remain safe while proving the DLP boundary`(output: CapturedOutput) {
        analyze()

        val finished = spans.finishedSpanItems
        assertTrue(finished.any { it.name == "document.dlp.analyze" })
        assertTrue(finished.any { it.name == "document.ingest" })
        assertTrue(finished.any { it.name == "ai.analyzeDocument" })
        val dlpSpan = finished.singleOrNull { it.name == "dlp.redaction" }
        assertNotNull(dlpSpan)
        assertEquals(true, dlpSpan.attributes.get(AttributeKey.booleanKey("dlp.redacted")))
        assertEquals(2L, dlpSpan.attributes.get(AttributeKey.longKey("dlp.replacement_count")))
        assertEquals("CONFIDENTIAL", dlpSpan.attributes.get(AttributeKey.stringKey("document.classification")))

        val spanDump = finished.joinToString("\n") { span ->
            buildString {
                append(span.name)
                append(' ')
                append(span.attributes.asMap())
                append(' ')
                append(span.events.map { event -> event.name to event.attributes.asMap() })
            }
        }
        assertFalse(spanDump.contains("finance@example-confidential.eu"))
        assertFalse(spanDump.contains("NL91ABNA0417164300"))

        val logs = output.out
        assertTrue(logs.contains("DLP demo document accepted: documentId=KTCONF-DLP-001, classification=CONFIDENTIAL"))
        assertTrue(logs.contains("AI operation completed: documentId=KTCONF-DLP-001"))
        assertTrue(logs.contains("DLP redaction applied: operation=analyzeDocument, rule=email, replacementCount=1"))
        assertTrue(logs.contains("DLP redaction applied: operation=analyzeDocument, rule=iban, replacementCount=1"))
        assertFalse(logs.contains("finance@example-confidential.eu"))
        assertFalse(logs.contains("NL91ABNA0417164300"))
    }

    private fun analyze(): JsonNode {
        val bytes = requireNotNull(
            javaClass.classLoader.getResourceAsStream("fixtures/dlp-confidential-document.pdf"),
        ).readBytes()
        val started = mockMvc.perform(
            multipart("/documents/dlp/analyze")
                .file(MockMultipartFile("file", "dlp-confidential-document.pdf", "application/pdf", bytes)),
        ).andReturn()
        val response = mockMvc.perform(asyncDispatch(started)).andReturn().response
        assertEquals(200, response.status, response.contentAsString)
        return objectMapper.readTree(response.contentAsString)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestTelemetryConfiguration {
        @Bean
        fun inMemorySpanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

        @Bean
        @Primary
        fun testOpenTelemetry(exporter: InMemorySpanExporter): OpenTelemetry =
            OpenTelemetrySdk.builder()
                .setTracerProvider(
                    SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build(),
                )
                .build()
    }
}
