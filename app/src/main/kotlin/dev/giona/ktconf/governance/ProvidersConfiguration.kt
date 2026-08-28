package dev.giona.ktconf.governance

import dev.giona.ktconf.demo.DeterministicProvider
import dev.giona.ktconf.demo.cloudScript
import dev.giona.ktconf.demo.localScript
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.openai.OpenAiCompatibleProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory

/**
 * The ONLY infrastructure configuration in the application.
 *
 * Two ModelProvider beans coexist in the same Spring context and the same
 * [dev.tramai.sovereign.SovereignTramaiRuntime]:
 * - `local-provider` (trust zone LOCAL, declared in application.yml)
 * - `cloud-provider` (trust zone GLOBAL_CLOUD, declared in application.yml)
 *
 * Each identity is REAL when its `ktconf.providers.*` endpoint is
 * configured, otherwise deterministic:
 *
 *   no config:            local → deterministic, cloud → deterministic
 *   local base-url set:   local → ModelAliasProvider(OpenAiCompatibleProvider)
 *                         → Qwen3.8-27B-UD-Q6_K on the z840 (Tailscale)
 *   cloud api-key set:    cloud → CountingModelProvider(ModelAliasProvider(
 *                         OpenAiCompatibleProvider)) → DeepSeek V4 Flash
 *
 * Both real providers can coexist in the one runtime. The trust zones are
 * operator assertions (application.yml), never derived from URLs. The
 * deterministic stage oracle never sets these variables (`preflight` and
 * `stage-up` unset BOTH local and cloud families), so the demo stays
 * deterministic regardless of what the operator's shell exports.
 */
@Configuration
@EnableConfigurationProperties(ProviderEndpoints::class)
class ProvidersConfiguration(
    private val endpoints: ProviderEndpoints,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun localProvider(): ModelProvider =
        if (endpoints.local.baseUrl.isNotBlank()) {
            log.info("Configuring local provider with an OpenAI-compatible endpoint")
            realProvider(endpoints.local, "local-provider")
        } else {
            log.info("Configuring deterministic local provider")
            DeterministicProvider(providerId = "local-provider", script = ::localScript)
        }

    // Deliberately the concrete counter type: GovernanceStatsController
    // injects it to expose /governance/stats. Works with deterministic and
    // real DeepSeek delegates alike — policy denies BEFORE complete(), so
    // the counter proves the delta-0 oracle for both.
    @Bean
    fun cloudProvider(): CountingModelProvider {
        val delegate: ModelProvider =
            if (endpoints.cloud.apiKey.isNotBlank()) {
                log.info("Configuring cloud provider with an OpenAI-compatible endpoint")
                realProvider(endpoints.cloud, "cloud-provider")
            } else {
                log.info("Configuring deterministic cloud provider")
                DeterministicProvider(providerId = "cloud-provider", script = ::cloudScript)
            }
        return CountingModelProvider(delegate)
    }

    private fun realProvider(endpoint: Endpoint, providerId: String): ModelProvider {
        val actualModel = endpoint.model.ifBlank {
            throw IllegalStateException("ktconf.providers.$providerId.model is required for a real provider")
        }
        return ModelAliasProvider(
            delegate = OpenAiCompatibleProvider.bearerToken(
                bearerToken = endpoint.apiKey.ifBlank { "local-dev" },
                baseUrl = endpoint.baseUrl,
                providerName = providerId,
            ),
            actualModel = actualModel,
        )
    }
}
