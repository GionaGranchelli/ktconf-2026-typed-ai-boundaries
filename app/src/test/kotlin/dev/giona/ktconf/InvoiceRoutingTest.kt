package dev.giona.ktconf

import dev.giona.ktconf.api.AnalyzeResponse
import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.api.StatsResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals

/**
 * "Classification is supplied. Routing chooses. Policy enforces."
 *
 * Normal routes: PUBLIC → cloud operation, RESTRICTED → local operation.
 * The 200 envelope exposes the selected route so the stage can SEE routing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InvoiceRoutingTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun analyze(body: Any): org.springframework.http.ResponseEntity<AnalyzeResponse> =
        rest.exchange("/invoices/analyze", HttpMethod.POST, HttpEntity(body, headers()), AnalyzeResponse::class.java)

    @Test
    fun `PUBLIC request routes to cloud and returns typed result`() {
        val response = analyze(DemoRequests.typed())
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("KTCONF-001", body.assessment.invoiceId)
        assertEquals("LOW", body.assessment.risk.name)
        assertEquals("CLOUD", body.selectedRoute.name)
        // The cloud provider WAS invoked for PUBLIC data.
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, stats.cloudInvocationCount)
    }

    @Test
    fun `RESTRICTED request routes to local and never touches cloud`() {
        val response = analyze(DemoRequests.restricted())
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("LOCAL", body.selectedRoute.name)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, stats.localInvocationCount)
        assertEquals(0, stats.cloudInvocationCount, "RESTRICTED data must never reach the cloud provider")
    }

    @Test
    fun `forced RESTRICTED to cloud is denied before provider invocation`() {
        val response = rest.exchange(
            "/invoices/boundary/restricted-cloud",
            HttpMethod.POST,
            HttpEntity(DemoRequests.restricted(), headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("classification-routing-blocked", response.body!!.code)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.cloudInvocationCount, "denied route must never invoke the cloud provider")
        assertEquals(0, stats.paymentExecutionCount)
    }

    @Test
    fun `CONFIDENTIAL EU request uses EU Scaleway identity`() {
        val response = rest.exchange(
            "/invoices/eu-scaleway",
            HttpMethod.POST,
            HttpEntity(DemoRequests.request(dev.tramai.core.policy.DataClassification.CONFIDENTIAL, "KTCONF-001", "KTConf Catering BV", 42_830, "Catering"), headers()),
            AnalyzeResponse::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("EU_CLOUD", response.body!!.selectedRoute.name)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, stats.euScalewayInvocationCount)
    }

    @Test
    fun `forced RESTRICTED to EU is denied before Scaleway invocation`() {
        val response = rest.exchange(
            "/invoices/boundary/restricted-eu",
            HttpMethod.POST,
            HttpEntity(DemoRequests.restricted(), headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("classification-routing-blocked", response.body!!.code)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.euScalewayInvocationCount)
    }
}
