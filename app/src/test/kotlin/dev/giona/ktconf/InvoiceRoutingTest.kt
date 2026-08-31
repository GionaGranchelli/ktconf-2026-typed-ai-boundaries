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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import kotlin.test.assertEquals

/**
 * "Classification is supplied. Routing chooses. Policy enforces."
 *
 * Normal routes: PUBLIC → cloud operation, RESTRICTED → local operation.
 * The 200 envelope exposes the selected route so the stage can SEE routing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InvoiceRoutingTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mockMvc: MockMvc

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
    fun `forced RESTRICTED to GLOBAL NVIDIA is denied before provider invocation`() {
        val before = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.globalNvidiaInvocationCount
        val response = rest.exchange("/invoices/global-nvidia", HttpMethod.POST, HttpEntity(DemoRequests.restricted(), headers()), ErrorResponse::class.java)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("classification-routing-blocked", response.body!!.code)
        val after = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.globalNvidiaInvocationCount
        assertEquals(0, after - before)
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

    @Test
    fun `malformed multipart PDF is rejected without invoking any provider`() {
        val result = mockMvc.perform(
            multipart("/invoices/analyze-pdf")
                .file(MockMultipartFile("file", "invoice.pdf", "application/pdf", "not a PDF".toByteArray())),
        ).andReturn()
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isBadRequest)

        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.localInvocationCount)
        assertEquals(0, stats.localNvidiaInvocationCount)
        assertEquals(0, stats.euScalewayInvocationCount)
        assertEquals(0, stats.globalNvidiaInvocationCount)
        assertEquals(0, stats.cloudInvocationCount)
    }

    @Test
    fun `missing trusted PDF metadata is rejected without invoking any provider`() {
        val original = requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/public-invoice.pdf")).readBytes()
        val withoutClassification = String(original, Charsets.ISO_8859_1)
            .replace("/KTCONF-Classification (PUBLIC)", "")
            .toByteArray(Charsets.ISO_8859_1)
        val started = mockMvc.perform(
            multipart("/invoices/analyze-pdf")
                .file(MockMultipartFile("file", "missing-metadata.pdf", "application/pdf", withoutClassification)),
        ).andReturn()
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isBadRequest)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.localInvocationCount)
        assertEquals(0, stats.localNvidiaInvocationCount)
        assertEquals(0, stats.euScalewayInvocationCount)
        assertEquals(0, stats.globalNvidiaInvocationCount)
        assertEquals(0, stats.cloudInvocationCount)
    }

    @Test
    fun `trusted PDF residency selects the corresponding governed boundary`() {
        val cases = listOf(
            "fixtures/public-invoice.pdf" to "GLOBAL_CLOUD",
            "fixtures/confidential-eu-invoice.pdf" to "EU_CLOUD",
            "fixtures/restricted-local-invoice.pdf" to "LOCAL_NVIDIA",
        )
        cases.forEach { (resource, route) ->
            val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)).readBytes()
            val initial = mockMvc.perform(
                multipart("/invoices/analyze-pdf")
                    .file(MockMultipartFile("file", resource.substringAfterLast('/'), "application/pdf", bytes)),
            ).andReturn()
            val response = mockMvc.perform(asyncDispatch(initial)).andReturn().response
            assertEquals(HttpStatus.OK.value(), response.status, "resource=$resource body=${response.contentAsString}")
            assertEquals(true, response.contentAsString.contains("\"selectedRoute\":\"$route\""))
        }

        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, stats.globalNvidiaInvocationCount)
        assertEquals(1, stats.euScalewayInvocationCount)
        assertEquals(1, stats.localNvidiaInvocationCount)
    }

    @Test
    fun `confidential EU PDF forced global is denied before global invocation then succeeds in EU`() {
        val resource = "fixtures/confidential-eu-invoice.pdf"
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)).readBytes()
        fun upload(path: String) = mockMvc.perform(
            multipart(path).file(MockMultipartFile("file", "confidential-eu-invoice.pdf", "application/pdf", bytes)),
        ).andReturn()

        val before = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.globalNvidiaInvocationCount
        val denied = mockMvc.perform(asyncDispatch(upload("/invoices/boundary/confidential-eu-global")))
            .andReturn().response
        assertEquals(HttpStatus.FORBIDDEN.value(), denied.status)
        assertEquals(true, denied.contentAsString.contains("classification-routing-blocked"))
        val afterDenied = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.globalNvidiaInvocationCount
        assertEquals(0, afterDenied - before)

        val allowed = mockMvc.perform(asyncDispatch(upload("/invoices/analyze-pdf"))).andReturn().response
        assertEquals(HttpStatus.OK.value(), allowed.status)
        assertEquals(true, allowed.contentAsString.contains("\"selectedRoute\":\"EU_CLOUD\""))
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, stats.euScalewayInvocationCount)
        assertEquals(before, stats.globalNvidiaInvocationCount)
    }
}
