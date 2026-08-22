package dev.giona.ktconf

import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.scenarios.TypedBoundaryScenario
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Typed boundary: a valid provider response becomes an actual
 * [dev.giona.ktconf.domain.InvoiceAssessment] through real TramAI
 * structured output.
 */
class TypedBoundaryScenarioTest {

    @Test
    fun `valid provider response maps to typed assessment`() = runBlocking {
        val result = TypedBoundaryScenario().run()

        assertEquals("KTCONF-001", result.assessment.invoiceId)
        assertEquals("KTConf Catering BV", result.assessment.supplierName)
        assertEquals(42_830L, result.assessment.amountCents)
        assertEquals("EUR", result.assessment.currency)
        assertEquals(InvoiceRisk.LOW, result.assessment.risk)
        assertEquals(InvoiceAction.REVIEW_ONLY, result.assessment.recommendedAction)
    }
}
