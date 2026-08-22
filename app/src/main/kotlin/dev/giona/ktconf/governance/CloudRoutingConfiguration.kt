package dev.giona.ktconf.governance

import dev.giona.ktconf.application.InvoiceAnalyzer
import dev.giona.ktconf.demo.DemoResponses
import dev.giona.ktconf.demo.ScriptedProvider
import dev.giona.ktconf.demo.demoScript
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.giona.ktconf.payments.SchedulePaymentTool
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
 * Cloud-routing profile: the model routes to a GLOBAL_CLOUD provider.
 * TramAI's classification-aware routing must deny RESTRICTED data BEFORE
 * provider invocation (cloud invocation count stays 0). The LOCAL provider
 * is registered and allowed — the same application on the demo profile
 * serves RESTRICTED data through it. Two configurations, same artifact:
 * the demo never claims automatic rerouting.
 */
@Configuration
@Profile("cloud-routing")
class CloudRoutingConfiguration {

    @Bean(name = ["cloudProvider"])
    fun cloudProvider(): ScriptedProvider =
        ScriptedProvider(CLOUD_PROVIDER) { _, _ -> DemoResponses.restrictedAdvisoryAssessment }

    @Bean
    fun localProvider(): ScriptedProvider =
        ScriptedProvider(LOCAL_PROVIDER, ::demoScript)

    @Bean
    fun tramaiCloud(
        clock: Clock,
        approvalStore: InMemoryApprovalStore,
        continuationStore: InMemoryApprovalContinuationStore,
        auditStore: InMemoryAuditStore,
        gateCoordinator: DefaultApprovalGateCoordinator,
        ledger: InMemoryPaymentLedger,
        cloudProvider: ScriptedProvider,
        localProvider: ScriptedProvider,
    ): SovereignTramai = buildSovereign(
        providers = mapOf(
            CLOUD_PROVIDER to cloudProvider,
            LOCAL_PROVIDER to localProvider,
        ),
        modelProvider = CLOUD_PROVIDER,
        providerZones = mapOf(
            CLOUD_PROVIDER to ProviderTrustZone.GLOBAL_CLOUD,
            LOCAL_PROVIDER to ProviderTrustZone.LOCAL,
        ),
        clock = clock,
        approvalStore = approvalStore,
        continuationStore = continuationStore,
        auditStore = auditStore,
        gateCoordinator = gateCoordinator,
        tool = SchedulePaymentTool(ledger),
    )

    @Bean
    fun sovereignRuntimeCloud(tramaiCloud: SovereignTramai): SovereignTramaiRuntime = tramaiCloud.runtime()

    @Bean
    fun invoiceAnalyzerCloud(runtime: SovereignTramaiRuntime): InvoiceAnalyzer {
        val service = runtime.create(dev.giona.ktconf.ai.InvoiceAnalysisService::class)
        return InvoiceAnalyzer { service.analyze(it) }
    }
}
