package dev.giona.ktconf.runtime

import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.SchedulePaymentResult
import dev.giona.ktconf.tools.InMemoryPaymentLedger
import dev.giona.ktconf.tools.SchedulePaymentTool
import dev.tramai.core.approval.ApprovalIdGenerator
import dev.tramai.core.approval.ApprovalToken
import dev.tramai.core.approval.ApprovalTokenGenerator
import dev.tramai.core.model.RegisteredModel
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
import dev.tramai.sovereign.SovereignTramaiRuntime
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Fixed demo clock — every scenario is deterministic and reproducible.
 */
val demoClock: Clock = Clock.fixed(
    Instant.parse("2026-06-11T12:00:00Z"),
    ZoneId.of("UTC"),
)

const val INVOICE_MODEL = "invoice-model"
const val LOCAL_PROVIDER = "local-provider"
const val CLOUD_PROVIDER = "conference-cloud-provider"

/**
 * A fully-wired, real TramAI sovereign runtime plus the stores the demo
 * needs to inspect afterward (approvals, audit, ledger).
 */
class DemoRuntime(
    val tramai: SovereignTramai,
    val runtime: SovereignTramaiRuntime,
    val approvalStore: InMemoryApprovalStore,
    val auditStore: InMemoryAuditStore,
    val ledger: InMemoryPaymentLedger,
) : AutoCloseable by runtime

/**
 * Builds real TramAI sovereign runtimes per scenario.
 *
 * The model "invoice-model" is always registered; [modelProvider] decides
 * which provider owns it. Provider trust zones come from [providerZones]
 * and drive TramAI's classification-aware routing (RESTRICTED → LOCAL only).
 */
class DemoRuntimeFactory(
    private val clock: Clock = demoClock,
) {

    fun sovereign(
        providers: Map<String, ScriptedProvider>,
        modelProvider: String,
        providerZones: Map<String, ProviderTrustZone>,
        ledger: InMemoryPaymentLedger = InMemoryPaymentLedger(),
    ): DemoRuntime {
        require(modelProvider in providers) { "modelProvider must be one of the registered providers" }
        require(providerZones.keys == providers.keys) {
            "providerZones must cover exactly the registered providers"
        }

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

        val builder = SovereignTramai.builder()
            .profile(
                SovereignProfileConfiguration(
                    allowedModels = setOf(INVOICE_MODEL),
                    allowedProviders = providers.keys,
                    allowedTools = setOf("schedule-payment"),
                    allowedPermissions = setOf("payment.schedule"),
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
            .tools(SchedulePaymentTool(ledger))

        providers.forEach { (id, provider) ->
            builder.provider(provider, name = id, default = (id == modelProvider))
        }
        builder.model(INVOICE_MODEL, modelProvider)

        val tramai = builder.build()
        return DemoRuntime(
            tramai = tramai,
            runtime = tramai.runtime(),
            approvalStore = approvalStore,
            auditStore = auditStore,
            ledger = ledger,
        )
    }
}

/** Convenience: a runtime with a single LOCAL provider. */
fun DemoRuntimeFactory.local(
    provider: ScriptedProvider,
    ledger: InMemoryPaymentLedger = InMemoryPaymentLedger(),
): DemoRuntime = sovereign(
    providers = mapOf(LOCAL_PROVIDER to provider),
    modelProvider = LOCAL_PROVIDER,
    providerZones = mapOf(LOCAL_PROVIDER to ProviderTrustZone.LOCAL),
    ledger = ledger,
)
