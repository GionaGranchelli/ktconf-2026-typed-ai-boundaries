package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.DemoResponses
import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.runtime.CLOUD_PROVIDER
import dev.giona.ktconf.runtime.DemoRuntimeFactory
import dev.giona.ktconf.runtime.LOCAL_PROVIDER
import dev.giona.ktconf.runtime.local
import dev.tramai.core.exception.PolicyViolationException
import dev.tramai.security.ProviderTrustZone

/**
 * Scenario 3 — Restricted data.
 *
 * KTCONF-RESTRICTED-001 is classified RESTRICTED. TramAI's
 * classification-aware routing (sovereign defaults: RESTRICTED → LOCAL
 * only) must deny the GLOBAL_CLOUD provider BEFORE invoking it, then
 * produce a typed result through an allowed LOCAL provider.
 *
 * No conference code checks the classification — the policy engine does.
 */
class RestrictedDataScenario(
    private val factory: DemoRuntimeFactory = DemoRuntimeFactory(),
) {
    suspend fun run(): RestrictedDataResult {
        val cloud = ScriptedProvider(CLOUD_PROVIDER, listOf(DemoResponses.cateringAssessment))
        val local = ScriptedProvider(LOCAL_PROVIDER, listOf(DemoResponses.restrictedAdvisoryAssessment))

        // Runtime A: invoice-model routes to the cloud provider.
        factory.sovereign(
            providers = mapOf(CLOUD_PROVIDER to cloud, LOCAL_PROVIDER to local),
            modelProvider = CLOUD_PROVIDER,
            providerZones = mapOf(
                CLOUD_PROVIDER to ProviderTrustZone.GLOBAL_CLOUD,
                LOCAL_PROVIDER to ProviderTrustZone.LOCAL,
            ),
        ).use { runtime ->
            val service = runtime.runtime.create(InvoiceAnalysisService::class)
            val denial = try {
                service.analyze(DemoInvoices.restrictedAdvisory)
                error("Expected PolicyViolationException for RESTRICTED data on a cloud provider")
            } catch (e: PolicyViolationException) {
                e
            }
            check(cloud.invocationCount() == 0) {
                "cloud provider must never be invoked; got ${cloud.invocationCount()} invocations"
            }

            // Runtime B: same invoice, allowed LOCAL provider.
            val localRuntime = factory.local(local)
            val localAssessment = localRuntime.runtime
                .create(InvoiceAnalysisService::class)
                .analyze(DemoInvoices.restrictedAdvisory)

            return RestrictedDataResult(
                denial = denial,
                cloudInvocationCount = cloud.invocationCount(),
                localAssessment = localAssessment,
            )
        }
    }
}
