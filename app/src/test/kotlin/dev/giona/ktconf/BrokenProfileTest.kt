package dev.giona.ktconf

import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.api.StatsResponse
import dev.giona.ktconf.domain.InvoiceDocument
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/**
 * Broken profile: the SAME application, an infrastructure bean whose model
 * emits garbage. The engine rejects the output (422) and no side effect
 * executes — the boundary holds, not the prompt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("broken")
class BrokenProfileTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `invalid model output is rejected with zero side effects`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(
                InvoiceDocument(
                    invoiceId = "KTCONF-001",
                    supplierName = "KTConf Catering BV",
                    amountCents = 42_830,
                    currency = "EUR",
                    description = "Catering",
                ),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("structured-output-rejected", response.body!!.code)

        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.paymentExecutionCount, "invalid output must not execute side effects")
    }
}
