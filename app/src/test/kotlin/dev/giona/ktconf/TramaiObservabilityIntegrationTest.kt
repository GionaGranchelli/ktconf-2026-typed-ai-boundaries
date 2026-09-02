package dev.giona.ktconf

import dev.giona.ktconf.api.AnalyzeResponse
import dev.giona.ktconf.api.AwaitingApprovalResponse
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves the Spring-governed demo exports TramAI operation spans. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TramaiObservabilityIntegrationTest.TestTelemetryConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TramaiObservabilityIntegrationTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var spans: InMemorySpanExporter

    @Test
    fun `cloud analysis emits a TramAI OpenTelemetry attempt span`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(DemoRequests.typed(), headers),
            AnalyzeResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val span = spans.finishedSpanItems.last { it.name == "ai.analyzeCloudAutoPayment" }
        assertEquals("cloud-provider", span.attributes.get(AttributeKey.stringKey("gen_ai.system")))
        assertEquals("cloud-invoice-model", span.attributes.get(AttributeKey.stringKey("gen_ai.request.model")))
        assertTrue(span.attributes.get(AttributeKey.booleanKey("tramai.structured.parse_success")) == true)
    }

    @Test
    fun `approval notification emits a safe email span`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = rest.exchange(
            "/invoices/analyze/local-nvidia",
            HttpMethod.POST,
            HttpEntity(DemoRequests.payment(), headers),
            AwaitingApprovalResponse::class.java,
        )

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        val span = spans.finishedSpanItems.single { it.name == "approval.email.recorded" }
        assertEquals("fake-email", span.attributes.get(AttributeKey.stringKey("notification.channel")))
        assertEquals("approval-request", span.attributes.get(AttributeKey.stringKey("notification.kind")))
        assertEquals("RECORDED", span.attributes.get(AttributeKey.stringKey("notification.status")))
        assertEquals("approver@ktconf.example", span.attributes.get(AttributeKey.stringKey("notification.recipient")))
        assertTrue(span.attributes.get(AttributeKey.stringKey("email.body")) == null)
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
