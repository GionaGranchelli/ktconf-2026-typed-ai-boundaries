package dev.giona.ktconf

import dev.giona.ktconf.scenarios.RealTypedResult
import dev.giona.ktconf.scenarios.RealTypedScenario
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * typed --real: runs ONLY when KTCONF_DEMO_LOCAL_BASE_URL is set (opt-in —
 * CI has no live model endpoint, so this test is skipped there). Proves the
 * same typed boundary works against a real LLM, or that the engine rejects
 * real-model output it cannot type — both are the boundary proving itself.
 */
@EnabledIfEnvironmentVariable(named = "KTCONF_DEMO_LOCAL_BASE_URL", matches = ".+")
class RealTypedScenarioTest {

    @Test
    fun `real model output either types or is rejected by the engine`() = runBlocking {
        val result = RealTypedScenario().run()

        when (result) {
            is RealTypedResult.Success -> {
                assertEquals("KTCONF-001", result.assessment.invoiceId)
                assertTrue(result.assessment.risk.name.isNotBlank())
                assertTrue(result.assessment.recommendedAction.name.isNotBlank())
            }
            is RealTypedResult.Rejected -> {
                assertTrue(result.failure.attemptCount != null)
            }
        }
    }
}
