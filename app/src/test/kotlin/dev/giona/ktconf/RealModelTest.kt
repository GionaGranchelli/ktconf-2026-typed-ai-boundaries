package dev.giona.ktconf

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
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
import kotlin.test.assertNotNull

/**
 * Real profile: a real LLM behind the same typed boundary. Runs ONLY when
 * KTCONF_DEMO_LOCAL_BASE_URL is set (CI has no live endpoint). Since the
 * enum-schema fix the success path is REQUIRED — a rejection here fails the
 * test, it is not an acceptable outcome.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("real")
@EnabledIfEnvironmentVariable(named = "KTCONF_DEMO_LOCAL_BASE_URL", matches = ".+")
class RealModelTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `real model produces typed invoice assessment`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(
                InvoiceDocument(
                    invoiceId = "KTCONF-001",
                    supplierName = "KTConf Catering BV",
                    amountCents = 42_830,
                    currency = "EUR",
                    description = "Conference catering services",
                ),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        val a = assertNotNull(response.body)
        assertEquals("KTCONF-001", a.invoiceId)
        assertEquals("EUR", a.currency)
    }
}
