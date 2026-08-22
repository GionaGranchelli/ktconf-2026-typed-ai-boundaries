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
import org.springframework.http.ResponseEntity
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Demo-profile rehearsal, 20/20, fresh context per repetition: the FULL
 * deterministic oracle over HTTP —
 *   typed 200 → deny oracle (202 → deny 200 → resume 409 → payment 0)
 *   → approve 200 + exactly one payment → duplicate 409 → evidence
 *   (exact ordered 4-event timeline, chain valid).
 * The deny step runs BEFORE any payment so the ledger really is 0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RehearsalDemoIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun invoice(id: String, supplier: String, cents: Long, description: String) =
        InvoiceDocument(id, supplier, cents, "EUR", description)

    private inline fun <reified T> analyze(body: InvoiceDocument): ResponseEntity<T> =
        rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(body, headers()),
            T::class.java,
        )

    @RepeatedTest(20)
    fun `full deterministic oracle over HTTP`() {
        // 1. Typed boundary on the ordinary invoice.
        val typed = analyze<InvoiceAssessment>(
            invoice("KTCONF-001", "KTConf Catering BV", 42_830, "Catering"),
        )
        assertEquals(HttpStatus.OK, typed.statusCode)
        assertEquals("LOW", typed.body!!.risk.name)

        // 2. Deny oracle FIRST: 202 → deny → runtime refuses resume → payment 0.
        val deniedPending = analyze<AwaitingApprovalResponse>(
            invoice("KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage"),
        )
        assertEquals(HttpStatus.ACCEPTED, deniedPending.statusCode)
        val deniedId = assertNotNull(deniedPending.body).approvalId
        val denied = rest.exchange(
            "/approvals/$deniedId/deny",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            DenyView::class.java,
        )
        assertEquals(HttpStatus.OK, denied.statusCode)
        assertEquals("DENIED", denied.body!!.status)
        assertEquals(0, denied.body!!.paymentExecutionCount)
        val resumeAfterDeny = rest.exchange(
            "/approvals/$deniedId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, resumeAfterDeny.statusCode)
        assertEquals(
            0,
            rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount,
        )

        // 3. Approve flow: 202 → approve → exactly one payment.
        val pending = analyze<AwaitingApprovalResponse>(
            invoice("KTCONF-PAY-001", "KTConf AV & Stage Services BV", 1_840_000, "Stage"),
        )
        assertEquals(HttpStatus.ACCEPTED, pending.statusCode)
        val approvalId = assertNotNull(pending.body).approvalId
        val approved = rest.exchange(
            "/approvals/$approvalId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, approved.statusCode)
        assertEquals("SCHEDULE_PAYMENT", approved.body!!.recommendedAction.name)
        assertEquals(
            1,
            rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount,
        )

        // 4. Duplicate approve → rejected, payment still 1.
        val duplicate = rest.exchange(
            "/approvals/$approvalId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
        assertEquals(
            1,
            rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!.paymentExecutionCount,
        )

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
