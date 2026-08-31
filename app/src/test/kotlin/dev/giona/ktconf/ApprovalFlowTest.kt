package dev.giona.ktconf

import dev.giona.ktconf.api.AwaitingApprovalResponse
import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.api.StatsResponse
import dev.giona.ktconf.domain.InvoiceAssessment
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.mock.web.MockMultipartFile
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The full approval lifecycle over HTTP: suspend → approve → execute once →
 * duplicate rejected; deny → no payment → continuation refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApprovalFlowTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun post(path: String, body: Any?, type: Class<*>): org.springframework.http.ResponseEntity<*> =
        rest.exchange(path, HttpMethod.POST, HttpEntity(body, headers()), type)

    private fun stats(): StatsResponse =
        rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!

    @Test
    fun `payment suspends with 202 and zero payments`() {
        val response = post("/invoices/analyze", DemoRequests.payment(), AwaitingApprovalResponse::class.java)
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        val body = response.body as AwaitingApprovalResponse
        assertNotNull(body.approvalId)
        assertEquals(
            "Payment scheduling requires human approval because invoice KTCONF-PAY-001 is a high-risk write action.",
            body.rationale,
        )
        assertNotNull(body.workflowRunId)
        assertEquals("schedule-payment", body.toolName)
        assertEquals(0, stats().paymentExecutionCount)
    }

    @Test
    fun `approve resumes the workflow and executes exactly one payment`() {
        val pending = post("/invoices/analyze", DemoRequests.payment(), AwaitingApprovalResponse::class.java)
        val approvalId = (pending.body as AwaitingApprovalResponse).approvalId
        assertEquals(0, stats().paymentExecutionCount)

        val approved = post("/approvals/$approvalId/approve", null, InvoiceAssessment::class.java)
        assertEquals(HttpStatus.OK, approved.statusCode)
        assertEquals("SCHEDULE_PAYMENT", (approved.body as InvoiceAssessment).recommendedAction.name)
        assertEquals(1, stats().paymentExecutionCount, "approval must execute the payment exactly once")
    }

    @Test
    fun `duplicate approve is rejected and payment stays at one`() {
        val pending = post("/invoices/analyze", DemoRequests.payment(), AwaitingApprovalResponse::class.java)
        val approvalId = (pending.body as AwaitingApprovalResponse).approvalId
        post("/approvals/$approvalId/approve", null, InvoiceAssessment::class.java)
        assertEquals(1, stats().paymentExecutionCount)

        val duplicate = post("/approvals/$approvalId/approve", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertEquals("approval-rejected", (duplicate.body as ErrorResponse).code)
        assertEquals(1, stats().paymentExecutionCount, "duplicate approve must not double-execute")
    }

    @Test
    fun `deny keeps payment at zero and continuation is refused afterwards`() {
        val pending = post("/invoices/analyze", DemoRequests.payment(), AwaitingApprovalResponse::class.java)
        val approvalId = (pending.body as AwaitingApprovalResponse).approvalId

        val denied = post("/approvals/$approvalId/deny", null, DenyView::class.java)
        assertEquals(HttpStatus.OK, denied.statusCode)
        assertEquals("DENIED", (denied.body as DenyView).status)

        val resumeAfterDeny = post("/approvals/$approvalId/approve", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, resumeAfterDeny.statusCode)
        assertEquals(0, stats().paymentExecutionCount, "denied approval must never execute a payment")
    }

    @Test
    fun `local NVIDIA assessment uses the same TramAI approval and audit lifecycle`() {
        val pending = post(
            "/invoices/analyze/local-nvidia",
            DemoRequests.payment(),
            AwaitingApprovalResponse::class.java,
        )
        assertEquals(HttpStatus.ACCEPTED, pending.statusCode)
        val approval = pending.body as AwaitingApprovalResponse
        assertEquals("schedule-payment", approval.toolName)
        assertEquals(0, stats().paymentExecutionCount)
        assertEquals(1, stats().localNvidiaInvocationCount)

        val approved = post("/approvals/${approval.approvalId}/approve", null, InvoiceAssessment::class.java)
        assertEquals(HttpStatus.OK, approved.statusCode)
        assertEquals("SCHEDULE_PAYMENT", (approved.body as InvoiceAssessment).recommendedAction.name)
        assertEquals(1, stats().paymentExecutionCount)

        val duplicate = post("/approvals/${approval.approvalId}/approve", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertEquals(1, stats().paymentExecutionCount)

        val evidence = rest.getForEntity(
            "/approvals/${approval.approvalId}/evidence",
            EvidenceView::class.java,
        )
        assertEquals(HttpStatus.OK, evidence.statusCode)
        assertEquals(true, evidence.body!!.chainValid)
    }

    @Test
    fun `canonical payment PDF denial preserves zero payment and refuses continuation`() {
        val bytes = requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/payment-local-invoice.pdf")).readBytes()
        val started = mockMvc.perform(
            multipart("/invoices/analyze-pdf")
                .file(MockMultipartFile("file", "payment-local-invoice.pdf", "application/pdf", bytes)),
        ).andReturn()
        val response = mockMvc.perform(asyncDispatch(started)).andReturn().response
        assertEquals(HttpStatus.ACCEPTED.value(), response.status)
        assertEquals(true, response.contentAsString.contains("\"selectedRoute\":\"LOCAL_NVIDIA\""))
        assertEquals(true, response.contentAsString.contains("\"toolName\":\"schedule-payment\""))
        val approvalId = Regex("\\\"approvalId\\\":\\\"([^\\\"]+)").find(response.contentAsString)!!.groupValues[1]
        assertEquals(0, stats().paymentExecutionCount)

        val denied = post("/approvals/$approvalId/deny", null, DenyView::class.java)
        assertEquals(HttpStatus.OK, denied.statusCode)
        val resumeAfterDeny = post("/approvals/$approvalId/approve", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, resumeAfterDeny.statusCode)
        assertEquals(0, stats().paymentExecutionCount)
    }
}
