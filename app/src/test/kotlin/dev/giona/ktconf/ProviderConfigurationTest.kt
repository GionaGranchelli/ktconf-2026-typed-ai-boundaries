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

    private fun config(
        local: Endpoint = Endpoint(),
        localNvidia: Endpoint = Endpoint(),
        cloud: Endpoint = Endpoint(),
        euScaleway: Endpoint = Endpoint(),
        globalNvidia: Endpoint = Endpoint(),
    ) = ProvidersConfiguration(ProviderEndpoints(local = local, localNvidia = localNvidia, cloud = cloud, euScaleway = euScaleway, globalNvidia = globalNvidia))

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
    fun `local NVIDIA without endpoint stays deterministic`() {
        val local = config().localNvidiaProvider()
        assertTrue(local.delegate is DeterministicProvider)
    }

    @Test
    fun `local NVIDIA endpoint selects OpenAI-compatible provider with logical identity`() {
        val local = config(
            localNvidia = Endpoint(
                baseUrl = "http://127.0.0.1:8088/v1",
                model = "nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M",
            ),
        ).localNvidiaProvider()
        val alias = local.delegate as ModelAliasProvider
        assertEquals("nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M", alias.actualModel)
        assertEquals("local-nvidia-provider", alias.providerId())
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

    @Test
    fun `global NVIDIA without api-key stays deterministic`() {
        val global = config().globalNvidiaProvider()
        assertTrue(global.delegate is DeterministicProvider)
        assertEquals(0, global.invocationCount())
    }

    @Test
    fun `global NVIDIA api-key selects OpenAI-compatible provider with logical identity`() {
        val global = config(
            globalNvidia = Endpoint(
                baseUrl = "https://integrate.api.nvidia.com/v1",
                model = "nvidia/nemotron-3.5-lightning-30b-a3b",
                apiKey = "nvidia-test-key",
            ),
        ).globalNvidiaProvider()
        val alias = global.delegate as ModelAliasProvider
        assertEquals("nvidia/nemotron-3.5-lightning-30b-a3b", alias.actualModel)
        assertEquals("global-nvidia-provider", alias.providerId())
        assertTrue(alias.delegate is OpenAiCompatibleProvider)
    }

    @Test
    fun `EU Scaleway without endpoint stays deterministic`() {
        assertTrue(config().euScalewayProvider().delegate is DeterministicProvider)
    }

    @Test
    fun `EU Scaleway endpoint selects OpenAI-compatible provider with logical identity`() {
        val eu = config(
            euScaleway = Endpoint(
                baseUrl = "https://eu.example.invalid/v1",
                model = "mistral-small-24b-instruct-2501",
                apiKey = "eu-test-key",
            ),
        ).euScalewayProvider()
        val alias = eu.delegate as ModelAliasProvider
        assertEquals("mistral-small-24b-instruct-2501", alias.actualModel)
        assertEquals("eu-scaleway-provider", alias.providerId())
        assertTrue(alias.delegate is OpenAiCompatibleProvider)
    }
}
