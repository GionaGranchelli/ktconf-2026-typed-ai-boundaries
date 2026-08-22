package dev.giona.ktconf

import dev.giona.ktconf.scenarios.RestrictedDataScenario
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Restricted provider: RESTRICTED data must be denied on a cloud
 * provider BEFORE invocation — the provider counter stays at zero.
 */
class RestrictedDataScenarioTest {

    @Test
    fun `restricted data never reaches a cloud provider`() = runBlocking {
        val result = RestrictedDataScenario().run()

        assertEquals(0, result.cloudInvocationCount)
        assertTrue(result.denial.decision.reasonCode.isNotBlank())
    }

    @Test
    fun `same restricted input succeeds through allowed local provider`() = runBlocking {
        val result = RestrictedDataScenario().run()

        assertEquals("KTCONF-RESTRICTED-001", result.localAssessment.invoiceId)
    }
}
