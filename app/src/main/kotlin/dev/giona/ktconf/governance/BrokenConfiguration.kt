package dev.giona.ktconf.governance

import dev.giona.ktconf.application.InvoiceAnalyzer
import dev.giona.ktconf.demo.DemoResponses
import dev.giona.ktconf.demo.ScriptedProvider
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
 * Broken profile: the SAME application, an infrastructure bean that emits
 * garbage. Structured output is rejected by the engine (422) and no side
 * effect executes — the boundary holds, not the prompt.
 */
@Configuration
@Profile("broken")
class BrokenConfiguration {

    @Bean
    fun tramaiBroken(
        clock: Clock,
        approvalStore: InMemoryApprovalStore,
        continuationStore: InMemoryApprovalContinuationStore,
        auditStore: InMemoryAuditStore,
        gateCoordinator: DefaultApprovalGateCoordinator,
        ledger: InMemoryPaymentLedger,
    ): SovereignTramai = buildSovereign(
        providers = mapOf(
            LOCAL_PROVIDER to ScriptedProvider(LOCAL_PROVIDER) { _, _ -> DemoResponses.brokenAssessment },
        ),
        modelProvider = LOCAL_PROVIDER,
        providerZones = mapOf(LOCAL_PROVIDER to ProviderTrustZone.LOCAL),
        clock = clock,
        approvalStore = approvalStore,
        continuationStore = continuationStore,
        auditStore = auditStore,
        gateCoordinator = gateCoordinator,
        tool = SchedulePaymentTool(ledger),
    )

    @Bean
    fun sovereignRuntimeBroken(tramaiBroken: SovereignTramai): SovereignTramaiRuntime = tramaiBroken.runtime()

    @Bean
    fun invoiceAnalyzerBroken(runtime: SovereignTramaiRuntime): InvoiceAnalyzer {
        val service = runtime.create(dev.giona.ktconf.ai.InvoiceAnalysisService::class)
        return InvoiceAnalyzer { service.analyze(it) }
    }
}
