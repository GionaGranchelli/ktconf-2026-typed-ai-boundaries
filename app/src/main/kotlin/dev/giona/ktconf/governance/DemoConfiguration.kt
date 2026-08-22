package dev.giona.ktconf.governance

import dev.giona.ktconf.application.InvoiceAnalyzer
import dev.giona.ktconf.demo.ScriptedProvider
import dev.giona.ktconf.demo.demoScript
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.giona.ktconf.payments.SchedulePaymentTool
import dev.tramai.core.approval.ApprovalToken
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
 * Default profile: one coherent application. LOCAL scripted provider, the
 * full governed surface (structured output, policy, tools, approval, audit).
 * Every invoice is classified RESTRICTED/DECLARED by the application.
 */
@Configuration
@Profile("demo")
class DemoConfiguration {

    @Bean
    fun tramaiDemo(
        clock: Clock,
        approvalStore: InMemoryApprovalStore,
        continuationStore: InMemoryApprovalContinuationStore,
        auditStore: InMemoryAuditStore,
        gateCoordinator: DefaultApprovalGateCoordinator,
        ledger: InMemoryPaymentLedger,
    ): SovereignTramai = buildSovereign(
        providers = mapOf(
            LOCAL_PROVIDER to ScriptedProvider(LOCAL_PROVIDER, ::demoScript),
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
    fun sovereignRuntime(tramaiDemo: SovereignTramai): SovereignTramaiRuntime = tramaiDemo.runtime()

    @Bean
    fun invoiceAnalyzer(runtime: SovereignTramaiRuntime): InvoiceAnalyzer {
        val service = runtime.create(dev.giona.ktconf.ai.InvoiceAnalysisService::class)
        return InvoiceAnalyzer { service.analyze(it) }
    }
}
