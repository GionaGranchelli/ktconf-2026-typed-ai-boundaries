package dev.giona.ktconf

import dev.giona.ktconf.api.AwaitingApprovalResponse
import dev.giona.ktconf.api.StatsResponse
import dev.giona.ktconf.domain.InvoiceAssessment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Concurrent approval workflows must not cross state: distinct approval ids
 * and workflow runs, independent continuations, per-workflow evidence
 * isolation. The two workflows target DIFFERENT invoices, so the ledger
 * executes two payments (one per workflow) — any cross-talk would corrupt
 * ids, runs, or evidence streams.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentApprovalsTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    @Test
    fun `two simultaneous approval workflows do not cross state`() = runBlocking {
        val first = async { suspendPayment(DemoRequests.payment()) }
        val second = async { suspendPayment(DemoRequests.payment2()) }
        val (a, b) = awaitAll(first, second)

        assertNotEquals(a.approvalId, b.approvalId, "approval ids must be unique per suspension")
        assertNotEquals(a.workflowRunId, b.workflowRunId, "workflow runs must be unique per suspension")

        // Approve both concurrently — each resume uses its OWN continuation.
        val approveA = async { approve(a.approvalId) }
        val approveB = async { approve(b.approvalId) }
        val results = awaitAll(approveA, approveB)
        results.forEach { assertEquals(org.springframework.http.HttpStatus.OK, it.statusCode) }

        // Each workflow schedules its own payment: exactly one per workflow.
        assertEquals(2, stats().paymentExecutionCount)

        // Evidence isolation: each workflow sees exactly its own 4 events.
        val evidenceA = rest.getForEntity("/approvals/${a.approvalId}/evidence", EvidenceView::class.java).body!!
        val evidenceB = rest.getForEntity("/approvals/${b.approvalId}/evidence", EvidenceView::class.java).body!!
        assertTrue(evidenceA.chainValid && evidenceB.chainValid)
        assertEquals(4, evidenceA.eventCount)
        assertEquals(4, evidenceB.eventCount)
        assertEquals(setOf(a.workflowRunId), evidenceA.auditEvents.map { it["workflowRunId"] }.toSet())
        assertEquals(setOf(b.workflowRunId), evidenceB.auditEvents.map { it["workflowRunId"] }.toSet())
    }

    private suspend fun suspendPayment(request: dev.giona.ktconf.domain.AnalyzeInvoiceRequest): AwaitingApprovalResponse {
        val response: ResponseEntity<AwaitingApprovalResponse> = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(request, headers()),
            AwaitingApprovalResponse::class.java,
        )
        return response.body!!
    }

    private suspend fun approve(approvalId: String): ResponseEntity<InvoiceAssessment> =
        rest.exchange(
            "/approvals/$approvalId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            InvoiceAssessment::class.java,
        )

    private fun stats(): StatsResponse =
        rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
}
