package dev.giona.ktconf

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.giona.ktconf.payments.AutoSchedulePaymentTool
import dev.giona.ktconf.payments.SchedulePaymentTool
import dev.tramai.core.model.RegisteredModel
import dev.tramai.security.ProviderTrustZone
import dev.tramai.security.audit.InMemoryAuditStore
import dev.tramai.security.model.InMemoryModelRegistry
import dev.tramai.sovereign.SovereignProfileConfiguration
import dev.tramai.sovereign.SovereignTramai
import dev.tramai.testing.MockAiProvider
import dev.tramai.testing.RecordingOperationObserver
import dev.tramai.testing.SimulatedFailureProvider
import dev.tramai.testing.TramaiAssertions
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the typed AI boundary with TramAI's deterministic testing module.
 * These tests never start Spring, use credentials, or contact a provider.
 */
class InvoiceAnalysisServiceTramaiTest {

    @Test
    fun `cloud analysis parses typed output after a malformed response retry`() {
        val provider = MockAiProvider {
            onMethod("analyzeCloud") respondWith "not JSON"
            onMethod("analyzeCloud") respondWith validAssessmentJson
        }
        val observer = RecordingOperationObserver()
        val service = testRuntime(provider, observer, "mock").create(InvoiceAnalysisService::class)

        val result = runBlocking { service.analyzeCloud(DemoRequests.typed().toClassifiedDocument()) }

        assertEquals("KTCONF-001", result.invoiceId)
        assertEquals(InvoiceRisk.LOW, result.risk)
        assertEquals(InvoiceAction.REVIEW_ONLY, result.recommendedAction)
        assertEquals(0.96, result.confidence)
        TramaiAssertions.assertThat(provider, observer)
            .whenCalled("analyzeCloud")
            .wasCalledTimes(2)
            .andRetried(1)
            .andParsedSuccessfully()
            .emittedProvider("mock")
    }

    @Test
    fun `cloud analysis recovers from a retryable provider failure`() {
        val provider = SimulatedFailureProvider {
            onMethod("analyzeCloud").retryableFailure("rate limited", statusCode = 429)
            onMethod("analyzeCloud") respondWith validAssessmentJson
        }
        val observer = RecordingOperationObserver()
        val service = testRuntime(provider, observer, "simulated-failure").create(InvoiceAnalysisService::class)

        val result = runBlocking { service.analyzeCloud(DemoRequests.typed().toClassifiedDocument()) }

        assertEquals("KTCONF-001", result.invoiceId)
        TramaiAssertions.assertThat(provider, observer)
            .whenCalled("analyzeCloud")
            .wasCalledTimes(2)
            .andRetried(1)
            .andObservedFailure(dev.tramai.core.exception.ProviderException::class)
            .andParsedSuccessfully()
            .emittedProvider("simulated-failure")
    }

    @Test
    fun `cloud analysis repairs confidence outside the TramAI field constraint`() {
        val provider = MockAiProvider {
            onMethod("analyzeCloud") respondWith validAssessmentJson.replace("0.96", "1.25")
            onMethod("analyzeCloud") respondWith validAssessmentJson
        }
        val observer = RecordingOperationObserver()
        val service = testRuntime(provider, observer, "mock").create(InvoiceAnalysisService::class)

        val result = runBlocking { service.analyzeCloud(DemoRequests.typed().toClassifiedDocument()) }

        assertEquals(0.96, result.confidence)
        TramaiAssertions.assertThat(provider, observer)
            .whenCalled("analyzeCloud")
            .wasCalledTimes(2)
            .andRetried(1)
            .andParsedSuccessfully()
            .emittedProvider("mock")
    }

    private fun testRuntime(
        provider: dev.tramai.core.provider.ModelProvider,
        observer: RecordingOperationObserver,
        providerName: String,
    ) = SovereignTramai.builder()
        .profile(
            SovereignProfileConfiguration(
                allowedModels = setOf("cloud-invoice-model", "local-invoice-model"),
                allowedProviders = setOf(providerName),
                providerZones = mapOf(providerName to ProviderTrustZone.LOCAL),
            ),
        )
        .modelRegistry(
            InMemoryModelRegistry.builder()
                .register(
                    RegisteredModel(
                        registryEntryId = "cloud-invoice-model",
                        providerId = providerName,
                        modelName = "cloud-invoice-model",
                        revision = "test",
                    ),
                )
                .register(
                    RegisteredModel(
                        registryEntryId = "local-invoice-model",
                        providerId = providerName,
                        modelName = "local-invoice-model",
                        revision = "test",
                    ),
                )
                .build(),
        )
        .auditStore(InMemoryAuditStore())
        .provider(provider, name = providerName, default = true)
        .model("cloud-invoice-model", providerName)
        .model("local-invoice-model", providerName)
        // The service declares this tool on analyzeLocal. Registering it keeps
        // the test runtime faithful to the production service contract even
        // though these cloud-route tests do not execute a tool call.
        .tools(listOf(
            SchedulePaymentTool(InMemoryPaymentLedger()),
            AutoSchedulePaymentTool(InMemoryPaymentLedger()),
        ))
        .observer(observer)
        .build()

    private companion object {
        val validAssessmentJson =
            """
            {
              "invoiceId": "KTCONF-001",
              "supplierName": "KTConf Catering BV",
              "amountCents": 42830,
              "currency": "EUR",
              "risk": "LOW",
              "recommendedAction": "REVIEW_ONLY",
              "confidence": 0.96,
              "rationale": "Conference catering services within budget"
            }
            """.trimIndent()
    }
}
