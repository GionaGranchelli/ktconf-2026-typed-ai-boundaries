package dev.giona.ktconf.governance

import dev.giona.ktconf.demo.DeterministicProvider
import dev.giona.ktconf.demo.cloudScript
import dev.giona.ktconf.demo.localScript
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.deepseek.DeepSeekProvider
import dev.tramai.openai.OpenAiCompatibleProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory

/**
 * Provider deployment configuration. Runtime composition lives separately in
 * the observability package so it can attach TramAI's OperationObserver.
 *
 * Two ModelProvider beans coexist in the same Spring context and the same
 * [dev.tramai.sovereign.SovereignTramaiRuntime]:
 * - `local-provider` (trust zone LOCAL, declared in application.yml)
 * - `local-nvidia-provider` (trust zone LOCAL, contest-only opt-in)
 * - `cloud-provider` (trust zone GLOBAL_CLOUD, declared in application.yml)
 * - `global-nvidia-provider` (trust zone GLOBAL_CLOUD, contest-only opt-in)
 * - `eu-nvidia-provider` (trust zone EU_CLOUD, contest-only opt-in)
 *
 * Each identity is REAL when its `ktconf.providers.*` endpoint is
 * configured, otherwise deterministic:
 *
 *   no config:            local → CountingModelProvider(deterministic),
 *                         cloud → CountingModelProvider(deterministic)
 *   local base-url set:   local → CountingModelProvider(ModelAliasProvider(OpenAiCompatibleProvider))
 *                         → Qwen3.8-27B-UD-Q6_K on the z840 (Tailscale)
 *   cloud api-key set:    cloud → CountingModelProvider(ModelAliasProvider(
 *                         DeepSeekProvider)) → DeepSeek V4 Flash
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
    fun localProvider(): CountingModelProvider {
        val delegate: ModelProvider =
            if (endpoints.local.baseUrl.isNotBlank()) {
                log.info("Configuring local provider with an OpenAI-compatible endpoint")
                realProvider(endpoints.local, "local-provider")
            } else {
                log.info("Configuring deterministic local provider")
                DeterministicProvider(providerId = "local-provider", script = ::localScript)
            }
        return CountingModelProvider(delegate)
    }

    /** Contest-only local NVIDIA identity; deterministic until an endpoint is configured. */
    @Bean
    fun localNvidiaProvider(): CountingModelProvider {
        val delegate: ModelProvider =
            if (endpoints.localNvidia.baseUrl.isNotBlank()) {
                log.info("Configuring local NVIDIA provider with an OpenAI-compatible endpoint")
                realProvider(endpoints.localNvidia, "local-nvidia-provider")
            } else {
                log.info("Configuring deterministic local NVIDIA provider")
                DeterministicProvider(providerId = "local-nvidia-provider", script = ::localScript)
            }
        return CountingModelProvider(delegate)
    }

    // Deliberately concrete counter beans: GovernanceStatsController injects
    // both to expose /governance/stats. They work with deterministic and real
    // delegates alike — policy denies BEFORE complete(), so the counter proves
    // the delta-0 oracle for either route.
    @Bean
    fun cloudProvider(): CountingModelProvider {
        val delegate: ModelProvider =
            if (endpoints.cloud.apiKey.isNotBlank()) {
                log.info("Configuring cloud provider with a DeepSeek endpoint")
                realProvider(endpoints.cloud, "cloud-provider")
            } else {
                log.info("Configuring deterministic cloud provider")
                DeterministicProvider(providerId = "cloud-provider", script = ::cloudScript)
            }
        return CountingModelProvider(delegate)
    }

    /** Contest-only hosted NVIDIA identity; deterministic until explicitly keyed. */
    @Bean
    fun globalNvidiaProvider(): CountingModelProvider {
        val delegate: ModelProvider =
            if (endpoints.globalNvidia.apiKey.isNotBlank()) {
                log.info("Configuring global NVIDIA provider with an OpenAI-compatible endpoint")
                realProvider(endpoints.globalNvidia, "global-nvidia-provider")
            } else {
                log.info("Configuring deterministic global NVIDIA provider")
                DeterministicProvider(providerId = "global-nvidia-provider", script = ::cloudScript)
            }
        return CountingModelProvider(delegate)
    }

    /** Nebius/NIM identity; deterministic until an authenticated endpoint is configured. */
    @Bean
    fun euNvidiaProvider(): CountingModelProvider {
        val delegate: ModelProvider =
            if (endpoints.euNvidia.baseUrl.isNotBlank() && endpoints.euNvidia.apiKey.isNotBlank()) {
                log.info("Configuring EU NVIDIA provider with an OpenAI-compatible endpoint")
                realProvider(endpoints.euNvidia, "eu-nvidia-provider")
            } else {
                log.info("Configuring deterministic EU NVIDIA provider")
                DeterministicProvider(providerId = "eu-nvidia-provider", script = ::cloudScript)
            }
        return CountingModelProvider(delegate)
    }

    private fun realProvider(endpoint: Endpoint, providerId: String): ModelProvider {
        val actualModel = endpoint.model.ifBlank {
            throw IllegalStateException("ktconf.providers.$providerId.model is required for a real provider")
        }
        log.info(
            "Real model provider configured: providerId={}, endpoint={}, actualModel={}",
            providerId,
            endpoint.baseUrl,
            actualModel,
        )
        val provider = if (providerId == "cloud-provider") {
            DeepSeekProvider(
                apiKey = endpoint.apiKey,
                baseUrl = endpoint.baseUrl,
            )
        } else {
            OpenAiCompatibleProvider.bearerToken(
                bearerToken = endpoint.apiKey.ifBlank { "local-dev" },
                baseUrl = endpoint.baseUrl,
                providerName = providerId,
            )
        }
        return ModelAliasProvider(
            delegate = provider,
            actualModel = actualModel,
            providerIdOverride = providerId,
        )
    }
}
