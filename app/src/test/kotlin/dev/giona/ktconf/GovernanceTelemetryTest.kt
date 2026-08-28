package dev.giona.ktconf

import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.tramai.core.exception.PolicyViolationException
import dev.tramai.core.policy.DataClassification
import dev.tramai.core.policy.PolicyDecision
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GovernanceTelemetryTest {
    private val exporter = InMemorySpanExporter.create()
    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val telemetry = GovernanceTelemetry(
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build(),
    )

    @AfterTest
    fun tearDown() {
        exporter.reset()
    }

    @BeforeTest
    fun resetSpans() {
        exporter.reset()
    }

    @Test
    fun `records the selected route without invoice content`() = runBlocking {
        telemetry.traceModelCall(DataClassification.RESTRICTED, InvoiceRoute.LOCAL) { "done" }

        val span = exporter.finishedSpanItems.single()
        assertEquals("invoice.model.call", span.name)
        assertEquals("RESTRICTED", span.attributes.get(AttributeKey.stringKey("invoice.classification")))
        assertEquals("local-invoice-model", span.attributes.get(AttributeKey.stringKey("tramai.logical_model")))
        assertEquals("local-provider", span.attributes.get(AttributeKey.stringKey("tramai.provider")))
    }

    @Test
    fun `records a policy denial on the route span`() = runBlocking {
        assertFailsWith<PolicyViolationException> {
            telemetry.traceModelCall(DataClassification.RESTRICTED, InvoiceRoute.CLOUD) {
                throw PolicyViolationException(
                    PolicyDecision.Deny("Restricted data cannot reach cloud", "classification-routing-blocked"),
                )
            }
        }

        val span = exporter.finishedSpanItems.single()
        assertEquals("ERROR", span.status.statusCode.name)
        val denial = span.events.single { it.name == "governance.policy.denied" }
        assertEquals("governance.policy.denied", denial.name)
        assertEquals(
            "classification-routing-blocked",
            denial.attributes.get(AttributeKey.stringKey("governance.reason_code")),
        )
    }
}
