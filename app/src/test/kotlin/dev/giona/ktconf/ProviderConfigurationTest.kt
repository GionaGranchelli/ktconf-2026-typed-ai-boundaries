package dev.giona.ktconf

import dev.giona.ktconf.demo.DeterministicProvider
import dev.giona.ktconf.governance.CountingModelProvider
import dev.giona.ktconf.governance.Endpoint
import dev.giona.ktconf.governance.ModelAliasProvider
import dev.giona.ktconf.governance.ProviderEndpoints
import dev.giona.ktconf.governance.ProvidersConfiguration
import dev.tramai.openai.OpenAiCompatibleProvider
import dev.tramai.deepseek.DeepSeekProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Provider-identity selection: the SAME identities become deterministic or
 * real OpenAI-compatible endpoints depending on `ktconf.providers.*`.
 * No network is ever touched here.
 */
class ProviderConfigurationTest {

    private fun config(local: Endpoint = Endpoint(), cloud: Endpoint = Endpoint()) =
        ProvidersConfiguration(ProviderEndpoints(local = local, cloud = cloud))

    @Test
    fun `no provider config keeps both identities deterministic`() {
        val providers = config()
        val local = providers.localProvider()
        assertTrue(local.delegate is DeterministicProvider)
        val cloud = providers.cloudProvider()
        assertTrue(cloud.delegate is DeterministicProvider)
    }

    @Test
    fun `local base-url produces a real-compatible provider with model alias`() {
        val local = config(
            local = Endpoint(baseUrl = "http://z840-tailscale:8088/v1", model = "Qwen3.8-27B-UD-Q6_K"),
        ).localProvider()
        val alias = local.delegate as ModelAliasProvider
        assertEquals("Qwen3.8-27B-UD-Q6_K", alias.actualModel)
        assertEquals("local-provider", alias.providerId())
        assertTrue(alias.delegate is OpenAiCompatibleProvider)
    }

    @Test
    fun `cloud api-key produces a real DeepSeek-compatible provider with model alias`() {
        val cloud = config(
            cloud = Endpoint(baseUrl = "https://api.deepseek.com", model = "deepseek-v4-flash", apiKey = "sk-test"),
        ).cloudProvider()
        val alias = cloud.delegate as ModelAliasProvider
        assertEquals("deepseek-v4-flash", alias.actualModel)
        assertEquals("cloud-provider", alias.providerId())
        assertTrue(alias.delegate is DeepSeekProvider)
    }

    @Test
    fun `cloud without api-key stays deterministic even with base-url present`() {
        val cloud = config(
            cloud = Endpoint(baseUrl = "https://api.deepseek.com", model = "deepseek-v4-flash"),
        ).cloudProvider()
        assertTrue(cloud.delegate is DeterministicProvider)
    }
}
