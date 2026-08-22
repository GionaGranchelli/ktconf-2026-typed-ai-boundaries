package dev.giona.ktconf

import dev.giona.ktconf.scenarios.RealTypedResult
import dev.giona.ktconf.scenarios.RealTypedScenario
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * typed --real: runs ONLY when KTCONF_DEMO_LOCAL_BASE_URL is set (opt-in — CI
 * has no live model endpoint, so this test is skipped there). Since tramAI#262
 * fixed the enum schema, the purpose of the real-model path is to prove a real
 * LLM SUCCEEDS through the typed boundary — rejection is no longer an expected
 * outcome here (the deterministic InvalidOutput scenario covers rejection).
 */
@EnabledIfEnvironmentVariable(named = "KTCONF_DEMO_LOCAL_BASE_URL", matches = ".+")
class RealTypedScenarioTest {

    @Test
    fun `real model produces typed invoice assessment`() = runBlocking {
        when (val result = RealTypedScenario().run()) {
            is RealTypedResult.Success -> {
                assertEquals("KTCONF-001", result.assessment.invoiceId)
                assertEquals("KTConf Catering BV", result.assessment.supplierName)
                assertEquals(42_830, result.assessment.amountCents)
                assertEquals("EUR", result.assessment.currency)
                assertTrue(result.assessment.risk.name.isNotBlank())
                assertTrue(result.assessment.recommendedAction.name.isNotBlank())
                assertTrue(result.assessment.rationale.isNotBlank())
            }
            is RealTypedResult.Rejected -> fail(
                "typed --real must produce a typed InvoiceAssessment, but the engine " +
                    "rejected the real model's output: " +
                    "attemptCount=${result.failure.attemptCount}, " +
                    "validationError=${result.failure.validationError}, " +
                    "lastRawResponse=${result.failure.lastRawResponse?.take(200)}",
            )
        }
    }
}
