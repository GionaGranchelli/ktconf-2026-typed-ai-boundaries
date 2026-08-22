package dev.giona.ktconf

import dev.giona.ktconf.scenarios.InvalidOutputScenario
import dev.tramai.core.exception.StructuredOutputException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invalid output: a broken model response is rejected by the real
 * TramAI structured-output engine — and produces zero side effects.
 */
class InvalidOutputScenarioTest {

    @Test
    fun `broken model output is rejected with zero side effects`() = runBlocking {
        val result = InvalidOutputScenario().run()

        assertTrue(result.failure is StructuredOutputException)
        assertEquals(0, result.paymentExecutionCount)
        // The engine's repair loop exhausts deterministically at the pinned
        // revision: initial attempt + two repairs. Lock the public claim.
        assertEquals(3, result.failure.attemptCount)
    }
}
