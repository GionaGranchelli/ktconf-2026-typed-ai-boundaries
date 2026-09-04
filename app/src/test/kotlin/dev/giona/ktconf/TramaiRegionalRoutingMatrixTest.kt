package dev.giona.ktconf

import dev.tramai.core.policy.DataClassification
import dev.tramai.core.policy.EnforcementPoint
import dev.tramai.core.policy.PolicyContext
import dev.tramai.core.policy.PolicyDecision
import dev.tramai.security.DefaultPolicyEngine
import dev.tramai.security.PolicyConfiguration
import dev.tramai.security.ProviderRoutingConfiguration
import dev.tramai.security.ProviderTrustZone
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Repository-level proof of the pinned TramAI regional policy matrix. */
class TramaiRegionalRoutingMatrixTest {
    private val providers = mapOf(
        "local" to ProviderTrustZone.LOCAL,
        "eu" to ProviderTrustZone.EU_CLOUD,
        "global" to ProviderTrustZone.GLOBAL_CLOUD,
    )
    private val engine = DefaultPolicyEngine(
        PolicyConfiguration.secure().copy(
            allowedModels = setOf("model"),
            allowedProviders = providers.keys,
            providerRouting = ProviderRoutingConfiguration(
                providerZones = providers,
                enabled = true,
            ),
        ),
    )

    @Test
    fun `every classification and provider zone follows sovereign defaults`() = runBlocking {
        val expected = mapOf(
            DataClassification.PUBLIC to setOf(ProviderTrustZone.LOCAL, ProviderTrustZone.EU_CLOUD, ProviderTrustZone.GLOBAL_CLOUD),
            DataClassification.INTERNAL to setOf(ProviderTrustZone.LOCAL, ProviderTrustZone.EU_CLOUD, ProviderTrustZone.GLOBAL_CLOUD),
            DataClassification.CONFIDENTIAL to setOf(ProviderTrustZone.LOCAL, ProviderTrustZone.EU_CLOUD),
            DataClassification.RESTRICTED to setOf(ProviderTrustZone.LOCAL),
        )

        for ((classification, allowedZones) in expected) {
            for ((provider, zone) in providers) {
                val decision = engine.evaluate(
                    PolicyContext(
                        enforcementPoint = EnforcementPoint.BEFORE_PROVIDER_INVOCATION,
                        correlationId = "regional-matrix-test",
                        actorId = "test",
                        policyVersion = "1.0.0",
                        modelName = "model",
                        providerId = provider,
                        dataClassification = classification,
                    ),
                )
                if (zone in allowedZones) {
                    assertEquals(PolicyDecision.Allow, decision, "$classification -> $zone")
                } else {
                    assertEquals("classification-routing-blocked", (decision as PolicyDecision.Deny).reasonCode)
                }
            }
        }
    }
}
