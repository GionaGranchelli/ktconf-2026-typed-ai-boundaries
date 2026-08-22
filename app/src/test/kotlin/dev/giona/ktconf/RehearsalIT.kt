package dev.giona.ktconf

import dev.giona.ktconf.api.AwaitingApprovalResponse
import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.api.StatsResponse
import dev.giona.ktconf.domain.InvoiceAssessment
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The 20/20 gate for the Spring variant: each repetition exercises the
 * ENTIRE HTTP storyline on a fresh context — typed 200, payment 202,
 * approve 200 + exactly one payment, duplicate 409 + still one, evidence
 * with the exact 4-event timeline. A fresh context per repetition proves
 * state isolation, not just context startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RehearsalIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun payInvoice() = InvoiceDocument(
        invoiceId = "KTCONF-PAY-001",
        supplierName = "KTConf AV & Stage Services BV",
        amountCents = 1_840_000,
        currency = "EUR",
        description = "Stage and AV production services",
    )

    @RepeatedTest(20)
    fun `full HTTP storyline - typed, suspended, approved, duplicate rejected, evidence valid`() {
        // 1. Typed boundary on the ordinary invoice.
        val typed = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(
                InvoiceDocument("KTCONF-001", "KTConf Catering BV", 42_830, "EUR", "Catering"),
                headers(),
            ),
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, typed.statusCode)
        assertEquals("LOW", typed.body!!.risk.name)

        // 2. HIGH-risk tool suspends the workflow.
        val pending = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(payInvoice(), headers()),
            AwaitingApprovalResponse::class.java,
        )
        assertEquals(HttpStatus.ACCEPTED, pending.statusCode)
        val approvalId = assertNotNull(pending.body).approvalId
        assertEquals(0, rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount)

        // 3. Approve → exactly one payment.
        val approved = rest.exchange(
            "/approvals/$approvalId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, approved.statusCode)
        assertEquals("SCHEDULE_PAYMENT", approved.body!!.recommendedAction.name)
        assertEquals(1, rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount)

        // 4. Duplicate approve → rejected, payment still 1.
        val duplicate = rest.exchange(
            "/approvals/$approvalId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertEquals(1, rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount)

        // 5. Evidence: exact ordered timeline, valid chain.
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
