package dev.giona.ktconf.governance

import dev.giona.ktconf.demo.DeterministicProvider
import dev.giona.ktconf.demo.cloudScript
import dev.giona.ktconf.demo.localScript
import dev.tramai.core.provider.ModelProvider
import dev.tramai.openai.OpenAiCompatibleProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * The ONLY infrastructure configuration in the application.
 *
 * Two ModelProvider beans coexist in the same Spring context and the same
 * [dev.tramai.sovereign.SovereignTramaiRuntime]:
 * - `local-provider` (trust zone LOCAL, declared in application.yml)
 * - `cloud-provider` (trust zone GLOBAL_CLOUD, declared in application.yml)
 *
 * The sovereign starter collects both beans and wires them into the runtime;
 * nothing else is constructed manually.
 *
 * REAL-MODEL OPT-IN: when `KTCONF_DEMO_LOCAL_BASE_URL` is set, the local
 * provider becomes a real OpenAI-compatible endpoint instead of the
 * deterministic script. That endpoint is declared LOCAL by operator
 * assertion — never by URL, hostname or provider type. The deterministic
 * stage oracle never sets these variables (`preflight` and `stage-up`
 * unset them), so the demo stays deterministic regardless of the network.
 */
@Configuration
class ProvidersConfiguration {

    @Bean
    fun localProvider(environment: Environment): ModelProvider {
        val baseUrl = environment.getProperty("KTCONF_DEMO_LOCAL_BASE_URL")
        if (baseUrl != null) {
            return realLocalProvider(environment, baseUrl)
        }
        return DeterministicProvider(providerId = "local-provider", script = ::localScript)
    }

    // Deliberately the concrete type: GovernanceStatsController injects this
    // bean to expose its invocation counter on /governance/stats.
    @Bean
    fun cloudProvider(): DeterministicProvider =
        DeterministicProvider(providerId = "cloud-provider", script = ::cloudScript)

    private fun realLocalProvider(
        environment: Environment,
        baseUrl: String,
    ): ModelProvider {
        val actualModel = environment.getProperty("KTCONF_DEMO_LOCAL_MODEL")
            ?: throw IllegalStateException(
                "KTCONF_DEMO_LOCAL_MODEL is required when KTCONF_DEMO_LOCAL_BASE_URL is set",
            )
        val apiKey = environment.getProperty("KTCONF_DEMO_LOCAL_API_KEY")
        // The logical route name stays "local-provider"; the alias swaps the
        // actual model id into every request (Ollama does not know
        // "local-invoice-model"). Trust zone LOCAL is an operator assertion.
        return ModelAliasProvider(
            delegate = OpenAiCompatibleProvider.bearerToken(
                bearerToken = apiKey?.takeIf { it.isNotBlank() } ?: "local-dev",
                baseUrl = baseUrl,
                providerName = "local-provider",
            ),
            actualModel = actualModel,
        )
    }
}
