package dev.giona.ktconf

import dev.giona.ktconf.api.AnalyzeResponse
import dev.giona.ktconf.api.AwaitingApprovalResponse
import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.api.StatsResponse
import dev.giona.ktconf.domain.InvoiceAssessment
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The conference gate: the FULL deterministic oracle, 20/20, fresh context
 * per repetition — one application, one runtime, every proof in sequence:
 *
 *   typed (PUBLIC → cloud 200) → restricted (RESTRICTED → local 200) →
 *   restricted-cloud (forced → 403, cloud invocation delta 0) → invalid
 *   (422, payment 0) → payment (202, payment 0) → deny oracle (deny 200 →
 *   resume 409, payment 0) → approve (200, payment 1) → duplicate (409,
 *   payment 1) → evidence (4 ordered events, chain valid).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RehearsalIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun analyze(body: Any): org.springframework.http.ResponseEntity<AnalyzeResponse> =
        rest.exchange("/invoices/analyze", HttpMethod.POST, HttpEntity(body, headers()), AnalyzeResponse::class.java)

    private fun post(path: String, body: Any?, type: Class<*>): org.springframework.http.ResponseEntity<*> =
        rest.exchange(path, HttpMethod.POST, HttpEntity(body, headers()), type)

    private fun stats(): StatsResponse =
        rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!

    @RepeatedTest(20)
    fun `full deterministic oracle over HTTP`() {
        // 1. Typed: PUBLIC → cloud route → typed result.
        val typed = analyze(DemoRequests.typed())
        assertEquals(HttpStatus.OK, typed.statusCode)
        assertEquals("CLOUD", typed.body!!.selectedRoute.name)
        assertEquals("KTCONF-001", typed.body!!.assessment.invoiceId)

        // 2. Restricted: RESTRICTED → local route → typed result, cloud untouched.
        val restricted = analyze(DemoRequests.restricted())
        assertEquals(HttpStatus.OK, restricted.statusCode)
        assertEquals("LOCAL", restricted.body!!.selectedRoute.name)
        assertEquals(1, stats().cloudInvocationCount, "cloud invoked exactly once (the PUBLIC call)")

        // 3. Forced RESTRICTED → cloud: policy denies BEFORE provider invocation.
        val forced = rest.exchange(
            "/invoices/boundary/restricted-cloud",
            HttpMethod.POST,
            HttpEntity(DemoRequests.restricted(), headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.FORBIDDEN, forced.statusCode)
        assertEquals("classification-routing-blocked", forced.body!!.code)
        assertEquals(1, stats().cloudInvocationCount, "denied route must not invoke the cloud provider")
        assertEquals(0, stats().paymentExecutionCount)

        // 4. Invalid output through the same app/runtime → 422, no side effects.
        val invalid = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(DemoRequests.invalid(), headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, invalid.statusCode)
        assertEquals("structured-output-rejected", invalid.body!!.code)
        assertEquals(0, stats().paymentExecutionCount)

        // 5. Deny oracle FIRST: 202 → deny → runtime refuses resume → payment 0.
        val deniedPending = post("/invoices/analyze", DemoRequests.payment(), AwaitingApprovalResponse::class.java)
        assertEquals(HttpStatus.ACCEPTED, deniedPending.statusCode)
        val deniedId = assertNotNull(deniedPending.body as AwaitingApprovalResponse).approvalId
        val denied = post("/approvals/$deniedId/deny", null, DenyView::class.java)
        assertEquals(HttpStatus.OK, denied.statusCode)
        assertEquals("DENIED", (denied.body as DenyView).status)
        val resumeAfterDeny = post("/approvals/$deniedId/approve", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, resumeAfterDeny.statusCode)
        assertEquals(0, stats().paymentExecutionCount)

        // 6. Approve flow: 202 → approve → exactly one payment.
        val pending = post("/invoices/analyze", DemoRequests.payment(), AwaitingApprovalResponse::class.java)
        assertEquals(HttpStatus.ACCEPTED, pending.statusCode)
        val approvalId = assertNotNull(pending.body as AwaitingApprovalResponse).approvalId
        val approved = post("/approvals/$approvalId/approve", null, InvoiceAssessment::class.java)
        assertEquals(HttpStatus.OK, approved.statusCode)
        assertEquals("SCHEDULE_PAYMENT", (approved.body as InvoiceAssessment).recommendedAction.name)
        assertEquals(1, stats().paymentExecutionCount)

        // 7. Duplicate approve → rejected, payment still 1.
        val duplicate = post("/approvals/$approvalId/approve", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertEquals(1, stats().paymentExecutionCount)

        // 8. Evidence: exact ordered timeline, chain valid, scoped to the workflow.
        val evidence = rest.getForEntity(
            "/approvals/$approvalId/evidence",
            EvidenceView::class.java,
        ).body!!
        assertTrue(evidence.chainValid)
        assertEquals(4, evidence.eventCount)
        assertEquals(
            listOf(
                "APPROVAL_SUSPENDED",
                "BEFORE_WORKFLOW_RESUME",
                "APPROVAL_RESUMED",
                "APPROVAL_COMPLETED",
            ),
            evidence.auditEvents.map { it["enforcementPoint"] },
        )
    }
}
