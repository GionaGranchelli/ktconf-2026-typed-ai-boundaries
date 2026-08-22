package dev.giona.ktconf.governance

import dev.giona.ktconf.application.InvoiceAnalyzer
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import dev.tramai.openai.OpenAiProvider
import dev.tramai.security.ProviderTrustZone
import dev.tramai.security.approval.DefaultApprovalGateCoordinator
import dev.tramai.security.approval.InMemoryApprovalContinuationStore
import dev.tramai.security.approval.InMemoryApprovalStore
import dev.tramai.security.audit.InMemoryAuditStore
import dev.tramai.sovereign.SovereignTramai
import dev.tramai.sovereign.SovereignTramaiRuntime
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Real profile: the SAME application, a real LLM behind the typed boundary.
 *
 * TRUST ZONE: the provider is declared LOCAL by operator assertion, never
 * by URL. KTCONF_DEMO_LOCAL_BASE_URL must point to an endpoint the operator
 * intentionally treats as LOCAL (Ollama on the laptop, private LAN,
 * self-hosted inference). Public cloud APIs must NOT be declared LOCAL.
 *
 * Deliberately no tools: the live-model smoke test receives LESS authority
 * than the deterministic governance service.
 */
@Configuration
@Profile("real")
class RealModelConfiguration {

    @Bean
    fun tramaiReal(
        clock: Clock,
        approvalStore: InMemoryApprovalStore,
        continuationStore: InMemoryApprovalContinuationStore,
        auditStore: InMemoryAuditStore,
        gateCoordinator: DefaultApprovalGateCoordinator,
        ledger: InMemoryPaymentLedger,
    ): SovereignTramai {
        val baseUrl = System.getenv("KTCONF_DEMO_LOCAL_BASE_URL")
            ?: throw IllegalStateException("real profile requires KTCONF_DEMO_LOCAL_BASE_URL")
        val modelName = System.getenv("KTCONF_DEMO_LOCAL_MODEL")
            ?: throw IllegalStateException("real profile requires KTCONF_DEMO_LOCAL_MODEL")
        val apiKey = System.getenv("KTCONF_DEMO_LOCAL_API_KEY") ?: "none"

        return buildSovereign(
            providers = mapOf(
                REAL_PROVIDER to ModelRenamingProvider(
                    delegate = OpenAiProvider(apiKey = apiKey, baseUrl = baseUrl),
                    realModel = modelName,
                ),
            ),
            modelProvider = REAL_PROVIDER,
            providerZones = mapOf(REAL_PROVIDER to ProviderTrustZone.LOCAL),
            clock = clock,
            approvalStore = approvalStore,
            continuationStore = continuationStore,
            auditStore = auditStore,
            gateCoordinator = gateCoordinator,
            ledger = ledger,
            tool = null,
        )
    }

    @Bean
    fun sovereignRuntimeReal(tramaiReal: SovereignTramai): SovereignTramaiRuntime = tramaiReal.runtime()

    @Bean
    fun invoiceAnalyzerReal(tramaiReal: SovereignTramai): InvoiceAnalyzer {
        val service = tramaiReal.runtime().create(dev.giona.ktconf.ai.RealInvoiceAnalysisService::class)
        return InvoiceAnalyzer { service.analyze(it) }
    }
}

/**
 * Maps the demo's internal model name ("invoice-model", referenced by the
 * compile-time @Operation annotation) to the actual model served by the
 * endpoint (KTCONF_DEMO_LOCAL_MODEL). The OpenAI-compatible provider sends
 * request.model verbatim, so the mapping happens here — before the provider
 * sees the request.
 */
private class ModelRenamingProvider(
    private val delegate: ModelProvider,
    private val realModel: String,
) : ModelProvider {
    override fun providerId(): String = delegate.providerId()
    override fun supportsCapability(capability: ProviderCapability): Boolean =
        delegate.supportsCapability(capability)
    override suspend fun complete(request: ModelRequest): ModelResponse =
        delegate.complete(request.copy(model = realModel))
}
