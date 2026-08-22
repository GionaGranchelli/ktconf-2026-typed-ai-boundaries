package dev.giona.ktconf

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.api.AwaitingApprovalResponse
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
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The typed boundary over HTTP (demo profile): valid model output → typed
 * result; HIGH-risk tool → 202 suspension; approve → exactly one payment;
 * duplicate approve → 409; deny → runtime refuses, payment 0; evidence →
 * the exact 4-event governance timeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApprovalApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun invoice(
        id: String,
        supplier: String,
        cents: Long,
        description: String,
    ) = InvoiceDocument(
        invoiceId = id,
        supplierName = supplier,
        amountCents = cents,
        currency = "EUR",
        description = description,
    )

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @Test
    fun `valid catering invoice produces a typed assessment`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(invoice("KTCONF-001", "KTConf Catering BV", 42_830, "Catering"), jsonHeaders()),
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        val a = assertNotNull(response.body)
        assertEquals("KTCONF-001", a.invoiceId)
        assertEquals("KTConf Catering BV", a.supplierName)
        assertEquals("EUR", a.currency)
        assertEquals("LOW", a.risk.name)
        assertEquals("REVIEW_ONLY", a.recommendedAction.name)
    }

    @Test
    fun `payment invoice suspends before execution`() {
        val response = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(invoice("KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage"), jsonHeaders()),
            AwaitingApprovalResponse::class.java,
        )
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        val pending = assertNotNull(response.body)
        assertEquals("AWAITING_APPROVAL", pending.status)
        assertEquals("schedule-payment", pending.toolName)
        assertTrue(pending.approvalId.isNotBlank())
        assertTrue(pending.workflowRunId.isNotBlank())

        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.paymentExecutionCount)
    }

    @Test
    fun `approved resume executes exactly once and duplicate resume is rejected`() {
        val pending = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(invoice("KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage"), jsonHeaders()),
            AwaitingApprovalResponse::class.java,
        ).body!!

        val approved = rest.exchange(
            "/approvals/${pending.approvalId}/approve",
            HttpMethod.POST,
            HttpEntity(null, jsonHeaders()),
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, approved.statusCode)
        val assessment = assertNotNull(approved.body)
        assertEquals("KTCONF-PAY-001", assessment.invoiceId)
        assertEquals("HIGH", assessment.risk.name)
        assertEquals("SCHEDULE_PAYMENT", assessment.recommendedAction.name)

        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, stats.paymentExecutionCount)

        // Duplicate resume: the runtime rejects it, payment stays 1.
        val duplicate = rest.exchange(
            "/approvals/${pending.approvalId}/approve",
            HttpMethod.POST,
            HttpEntity(null, jsonHeaders()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        val statsAfter = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(1, statsAfter.paymentExecutionCount)
    }

    @Test
    fun `deny preserves the v2 oracle - runtime refuses continuation and payment stays zero`() {
        val pending = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(invoice("KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage"), jsonHeaders()),
            AwaitingApprovalResponse::class.java,
        ).body!!

        val denied = rest.exchange(
            "/approvals/${pending.approvalId}/deny",
            HttpMethod.POST,
            HttpEntity(null, jsonHeaders()),
            DenyView::class.java,
        )
        assertEquals(HttpStatus.OK, denied.statusCode)
        assertEquals("DENIED", denied.body!!.status)
        assertEquals(0, denied.body!!.paymentExecutionCount)

        // Resuming after deny is refused by the runtime (409).
        val resumeAfterDeny = rest.exchange(
            "/approvals/${pending.approvalId}/approve",
            HttpMethod.POST,
            HttpEntity(null, jsonHeaders()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, resumeAfterDeny.statusCode)
        val stats = rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
        assertEquals(0, stats.paymentExecutionCount)
    }

    @Test
    fun `evidence is per workflow - exact ordered 4-event timeline, valid chain`() {
        val pending = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(invoice("KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage"), jsonHeaders()),
            AwaitingApprovalResponse::class.java,
        ).body!!
        rest.exchange(
            "/approvals/${pending.approvalId}/approve",
            HttpMethod.POST,
            HttpEntity(null, jsonHeaders()),
            InvoiceAssessment::class.java,
        )

        val evidence = rest.getForEntity(
            "/approvals/${pending.approvalId}/evidence",
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

    @Test
    fun `unknown approval id is a 404`() {
        val response = rest.exchange(
            "/approvals/nope/approve",
            HttpMethod.POST,
            HttpEntity(null, jsonHeaders()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("approval-not-found", response.body!!.code)
    }
}

data class DenyView(
    val approvalId: String,
    val status: String,
    val paymentExecutionCount: Int,
)

data class EvidenceView(
    val chainValid: Boolean,
    val eventCount: Int,
    val auditEvents: List<Map<String, Any>>,
)
