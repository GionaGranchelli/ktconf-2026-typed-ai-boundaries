package dev.giona.ktconf

import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.api.StatsResponse
import dev.giona.ktconf.domain.InvoiceDocument
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/**
 * Broken-profile rehearsal, 20/20, fresh context per repetition: the SAME
 * application with a garbage model bean → 422 and zero side effects.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("broken")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RehearsalBrokenIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    @RepeatedTest(20)
    fun `invalid model output rejected with zero side effects`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(
                InvoiceDocument("KTCONF-001", "KTConf Catering BV", 42_830, "EUR", "Catering"),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("structured-output-rejected", response.body!!.code)
        assertEquals(
            0,
            rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount,
        )
    }
}
