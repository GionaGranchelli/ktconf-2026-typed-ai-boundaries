package dev.giona.ktconf

import dev.giona.ktconf.governance.CountingModelProvider
import dev.giona.ktconf.governance.ModelAliasProvider
import dev.giona.ktconf.governance.ProviderEndpoints
import dev.tramai.core.provider.ModelProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression: a supplied cloud API key must select the REAL cloud provider
 * (not DeterministicProvider), and omitted cloud base-url/model must fall
 * through to the YAML DeepSeek defaults instead of being empty. Same for
 * the local model default when only the base-url is supplied.
 *
 * No network: beans are asserted structurally.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "ktconf.providers.local.base-url=http://127.0.0.1:9/v1",
        "ktconf.providers.cloud.api-key=sk-test",
        // cloud base-url/model and local model intentionally NOT set —
        // the YAML defaults must apply.
    ],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProviderDefaultsRegressionIT {

    @Autowired
    lateinit var context: org.springframework.context.ApplicationContext

    @Autowired
    lateinit var endpoints: ProviderEndpoints

    @Test
    fun `supplied cloud key selects real cloud with YAML DeepSeek defaults`() {
        // YAML defaults resolved by Spring binding:
        assertEquals("https://api.deepseek.com", endpoints.cloud.baseUrl)
        assertEquals("deepseek-v4-flash", endpoints.cloud.model)
        assertEquals("sk-test", endpoints.cloud.apiKey)

        val cloud = context.getBean("cloudProvider") as ModelProvider
        assertTrue(cloud is CountingModelProvider, "cloud must stay counted")
        val alias = (cloud as CountingModelProvider).delegate as ModelAliasProvider
        assertEquals("deepseek-v4-flash", alias.actualModel, "real cloud must use the YAML default model")
    }

    @Test
    fun `local base-url selects real local with the YAML GGUF model default`() {
        assertEquals("/home/fedora-workstation/models/Qwen3.8-27B-UD-Q6_K.gguf", endpoints.local.model)

        val local = context.getBean("localProvider") as ModelProvider
        assertTrue(local is ModelAliasProvider, "local must be real when base-url is set")
        assertEquals(
            "/home/fedora-workstation/models/Qwen3.8-27B-UD-Q6_K.gguf",
            (local as ModelAliasProvider).actualModel,
        )
    }
}
