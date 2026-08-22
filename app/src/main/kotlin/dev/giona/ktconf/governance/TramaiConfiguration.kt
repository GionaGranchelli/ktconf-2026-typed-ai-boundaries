package dev.giona.ktconf.governance

import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.tramai.core.approval.ApprovalIdGenerator
import dev.tramai.core.approval.ApprovalToken
import dev.tramai.core.approval.ApprovalTokenGenerator
import dev.tramai.core.model.RegisteredModel
import dev.tramai.core.model.TramaiTool
import dev.tramai.core.provider.ModelProvider
import dev.tramai.security.ProviderTrustZone
import dev.tramai.security.approval.DefaultApprovalGateCoordinator
import dev.tramai.security.approval.InMemoryApprovalContinuationStore
import dev.tramai.security.approval.InMemoryApprovalStore
import dev.tramai.security.approval.Sha256ApprovalTokenDigester
import dev.tramai.security.approval.Sha256ToolArgumentsDigester
import dev.tramai.security.audit.InMemoryAuditStore
import dev.tramai.security.model.InMemoryModelRegistry
import dev.tramai.sovereign.SovereignProfileConfiguration
import dev.tramai.sovereign.SovereignTramai
import java.time.Clock
import java.util.UUID
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Shared infrastructure beans — singleton by design, so an approval created
 * in one HTTP request can be resumed in the next. The per-profile
 * configurations build the SovereignTramai instance around them.
 */
@Configuration
class TramaiConfiguration {

    @Bean
    fun demoClock(): Clock = demoClock

    @Bean
    fun paymentLedger(): InMemoryPaymentLedger = InMemoryPaymentLedger()

    @Bean
    fun approvalStore(clock: Clock): InMemoryApprovalStore = InMemoryApprovalStore(clock = clock)

    @Bean
    fun continuationStore(clock: Clock): InMemoryApprovalContinuationStore =
        InMemoryApprovalContinuationStore(clock = clock)

    @Bean
    fun auditStore(): InMemoryAuditStore = InMemoryAuditStore()

    @Bean
    fun approvalGateCoordinator(approvalStore: InMemoryApprovalStore, clock: Clock): DefaultApprovalGateCoordinator =
        DefaultApprovalGateCoordinator(
            store = approvalStore,
            // Unique per suspension: the API hands the id to the client and
            // the client resumes BY id, so fixed ids would collide.
            approvalIdGenerator = ApprovalIdGenerator { "approval-ktconf-${UUID.randomUUID().toString().take(8)}" },
            // Unique challenge per suspension too: two concurrent approvals
            // must not hold interchangeable tokens. Nothing on stage prints
            // it — it only ever lives server-side in PendingApprovalRegistry.
            approvalTokenGenerator = ApprovalTokenGenerator {
                ApprovalToken.parsePresented("approval-token-${UUID.randomUUID()}")
            },
            approvalTokenDigester = Sha256ApprovalTokenDigester(),
            clock = clock,
        )
}

/**
 * Builds a SovereignTramai instance — the same wiring v2's factory used,
 * now parameterized per profile.
 */
internal fun buildSovereign(
    providers: Map<String, ModelProvider>,
    modelProvider: String,
    providerZones: Map<String, ProviderTrustZone>,
    clock: Clock,
    approvalStore: InMemoryApprovalStore,
    continuationStore: InMemoryApprovalContinuationStore,
    auditStore: InMemoryAuditStore,
    gateCoordinator: DefaultApprovalGateCoordinator,
    tool: TramaiTool<*, *>?,
): SovereignTramai {
    require(modelProvider in providers) { "modelProvider must be one of the registered providers" }
    require(providerZones.keys == providers.keys) { "providerZones must cover exactly the registered providers" }

    val builder = SovereignTramai.builder()
        .profile(
            SovereignProfileConfiguration(
                allowedModels = setOf(INVOICE_MODEL),
                allowedProviders = providers.keys,
                allowedTools = if (tool != null) setOf("schedule-payment") else emptySet(),
                allowedPermissions = if (tool != null) setOf("payment.schedule") else emptySet(),
                providerZones = providerZones,
            ),
        )
        .modelRegistry(
            InMemoryModelRegistry.builder()
                .register(
                    RegisteredModel(
                        registryEntryId = "invoice-model-$modelProvider-v1",
                        providerId = modelProvider,
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

    if (tool != null) builder.tools(tool)
    providers.forEach { (id, provider) ->
        builder.provider(provider, name = id, default = (id == modelProvider))
    }
    builder.model(INVOICE_MODEL, modelProvider)
    return builder.build()
}
