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
 * Cloud-routing rehearsal, 20/20, fresh context per repetition: RESTRICTED
 * data → 403 and the cloud provider's invocation counter stays 0 — the
 * killer invariant, denied BEFORE any provider call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cloud-routing")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RehearsalCloudIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    @RepeatedTest(20)
    fun `restricted data denied on cloud provider before invocation`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(
                InvoiceDocument("KTCONF-RESTRICTED-001", "ACME Acquisition Advisory", 8_250_000, "EUR", "MERGER-2026 advisory"),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("classification-routing-blocked", response.body!!.code)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.cloudInvocationCount, "cloud provider must never be invoked")
        assertEquals(0, stats.paymentExecutionCount)
    }
}
