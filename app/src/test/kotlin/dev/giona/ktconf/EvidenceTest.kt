package dev.giona.ktconf

import dev.giona.ktconf.api.AwaitingApprovalResponse
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evidence oracle: exactly four events in order, chain valid, scoped to the
 * queried workflow — not set membership, not the global store.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EvidenceTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    private fun headers() = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    @Test
    fun `evidence shows the exact ordered approval lifecycle with a valid chain`() {
        val pending = rest.exchange(
            "/invoices/analyze",
            HttpMethod.POST,
            HttpEntity(DemoRequests.payment(), headers()),
            AwaitingApprovalResponse::class.java,
        )
        val approvalId = pending.body!!.approvalId
        rest.exchange(
            "/approvals/$approvalId/approve",
            HttpMethod.POST,
            HttpEntity(null, headers()),
            InvoiceAssessment::class.java,
        )

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
        // All events belong to the same workflow run.
        val workflowRunIds = evidence.auditEvents.map { it["workflowRunId"] }.distinct()
        assertEquals(1, workflowRunIds.size, "evidence must be scoped to one workflow run")
    }
}
