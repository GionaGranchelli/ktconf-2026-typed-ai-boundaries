package dev.giona.ktconf

import dev.giona.ktconf.governance.CountingModelProvider
import dev.giona.ktconf.governance.ModelAliasProvider
import dev.tramai.core.provider.ModelProvider
import dev.tramai.sovereign.SovereignTramaiRuntime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Both REAL provider identities coexist in ONE context and ONE runtime —
 * no profiles, no second application. Nothing here performs a network
 * request; the beans are asserted structurally.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        // Local = z840 Qwen endpoint; Cloud = DeepSeek with a key.
        "ktconf.providers.local.base-url=http://127.0.0.1:9/v1",
        "ktconf.providers.local.model=Qwen3.8-27B-UD-Q6_K",
        "ktconf.providers.cloud.api-key=sk-test",
        "ktconf.providers.cloud.model=deepseek-v4-flash",
    ],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RealProvidersContextIT {

    @Autowired
    lateinit var context: org.springframework.context.ApplicationContext

    @Test
    fun `both real providers coexist in one runtime`() {
        val runtimes = context.getBeansOfType(SovereignTramaiRuntime::class.java)
        assertEquals(1, runtimes.size, "exactly one runtime even with two real providers")

        val local = context.getBean("localProvider") as ModelProvider
        assertTrue(local is ModelAliasProvider, "local must be the real z840 alias")
        assertEquals("local-provider", local.providerId())

        val cloud = context.getBean("cloudProvider") as ModelProvider
        assertTrue(cloud is CountingModelProvider, "cloud must stay counted even when real")
        assertTrue((cloud as CountingModelProvider).delegate is ModelAliasProvider)
        assertEquals("cloud-provider", cloud.providerId())
    }
}
