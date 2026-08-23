package dev.giona.ktconf

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
 * Invalid structured output through the SAME application and SAME runtime:
 * "Nothing about the application changed. Only the model response changed."
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InvalidOutputTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun analyze(body: Any): org.springframework.http.ResponseEntity<ErrorResponse> =
        rest.exchange("/invoices/analyze", HttpMethod.POST, HttpEntity(body, headers()), ErrorResponse::class.java)

    @Test
    fun `invalid output through cloud route is rejected with 422 and no side effects`() {
        val response = analyze(DemoRequests.invalid())
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("structured-output-rejected", response.body!!.code)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.paymentExecutionCount, "rejected output must not produce side effects")
    }

    @Test
    fun `invalid output through local route is rejected with 422`() {
        // RESTRICTED KTCONF-INVALID-001 → local route → same structured boundary.
        val response = analyze(
            DemoRequests.request(
                dev.tramai.core.policy.DataClassification.RESTRICTED,
                "KTCONF-INVALID-001", "KTConf", 42_830, "Broken",
            ),
        )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("structured-output-rejected", response.body!!.code)
    }
}
