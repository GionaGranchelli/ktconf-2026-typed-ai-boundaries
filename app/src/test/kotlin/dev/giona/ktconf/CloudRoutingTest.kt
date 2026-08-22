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
 * Cloud-routing profile: the model routes to a GLOBAL_CLOUD provider.
 * RESTRICTED data must be denied BEFORE provider invocation — the cloud
 * provider's invocation counter stays 0. Same endpoint, same body; only
 * the infrastructure bean differs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cloud-routing")
class CloudRoutingTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `restricted data is denied on the cloud provider before invocation`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(
                InvoiceDocument(
                    invoiceId = "KTCONF-RESTRICTED-001",
                    supplierName = "ACME Acquisition Advisory",
                    amountCents = 8_250_000,
                    currency = "EUR",
                    description = "MERGER-2026 advisory",
                ),
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
