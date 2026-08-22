package dev.giona.ktconf.runtime

import dev.tramai.core.approval.ApprovalIdGenerator
import dev.tramai.core.approval.ApprovalToken
import dev.tramai.core.approval.ApprovalTokenGenerator
import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.model.RegisteredModel
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import dev.tramai.openai.OpenAiProvider
import dev.tramai.security.ProviderTrustZone
import dev.tramai.security.approval.DefaultApprovalGateCoordinator
import dev.tramai.security.approval.InMemoryApprovalContinuationStore
import dev.tramai.security.approval.InMemoryApprovalStore
import dev.tramai.security.approval.Sha256ApprovalTokenDigester
import dev.tramai.security.approval.Sha256ToolArgumentsDigester
import dev.tramai.security.audit.InMemoryAuditStore
import dev.tramai.security.model.InMemoryModelRegistry
import dev.giona.ktconf.tools.InMemoryPaymentLedger
import dev.tramai.sovereign.SovereignProfileConfiguration
import dev.tramai.sovereign.SovereignTramai
import java.time.Clock

/**
 * Real-model runtime (typed --real only).
 *
 * Deliberately SEPARATE from [DemoRuntimeFactory]: the deterministic
 * Stage Candidate v1 factory stays frozen. This path wires an
 * OpenAI-compatible [OpenAiProvider] (Ollama / vLLM / llama.cpp / any
 * compatible endpoint) behind the same typed [dev.giona.ktconf.ai.InvoiceAnalysisService]
 * contract, as a LOCAL trust-zone provider, with no tools.
 */
class RealModelRuntimeFactory(
    private val clock: Clock = demoClock,
) {

    fun real(
        baseUrl: String,
        apiKey: String,
        modelName: String,
    ): DemoRuntime {
        val provider = ModelRenamingProvider(
            delegate = OpenAiProvider(apiKey = apiKey, baseUrl = baseUrl),
            realModel = modelName,
        )

        val approvalStore = InMemoryApprovalStore(clock = clock)
        val continuationStore = InMemoryApprovalContinuationStore(clock = clock)
        val auditStore = InMemoryAuditStore()
        val gateCoordinator = DefaultApprovalGateCoordinator(
            store = approvalStore,
            approvalIdGenerator = ApprovalIdGenerator { "approval-ktconf-001" },
            approvalTokenGenerator = ApprovalTokenGenerator {
                ApprovalToken.parsePresented("approval-token-ktconf-001")
            },
            approvalTokenDigester = Sha256ApprovalTokenDigester(),
            clock = clock,
        )

        val tramai = SovereignTramai.builder()
            .profile(
                SovereignProfileConfiguration(
                    allowedModels = setOf(INVOICE_MODEL),
                    allowedProviders = setOf(REAL_PROVIDER),
                    providerZones = mapOf(REAL_PROVIDER to ProviderTrustZone.LOCAL),
                ),
            )
            .modelRegistry(
                InMemoryModelRegistry.builder()
                    .register(
                        RegisteredModel(
                            registryEntryId = "invoice-model-real-v1",
                            providerId = REAL_PROVIDER,
                            modelName = INVOICE_MODEL,
                            revision = "1.0",
                        ),
                    )
                    .build(),
            )
            .auditStore(auditStore)
            .approvalContinuationStore(continuationStore)
            .toolArgumentsDigester(Sha256ToolArgumentsDigester())
            .approvalGateCoordinator(gateCoordinator)
            .clock(clock)
            .provider(provider, name = REAL_PROVIDER, default = true)
            .model(INVOICE_MODEL, REAL_PROVIDER)
            .build()

        return DemoRuntime(
            tramai = tramai,
            runtime = tramai.runtime(),
            approvalStore = approvalStore,
            auditStore = auditStore,
            ledger = InMemoryPaymentLedger(),
        )
    }

    companion object {
        const val REAL_PROVIDER = "real-provider"
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
