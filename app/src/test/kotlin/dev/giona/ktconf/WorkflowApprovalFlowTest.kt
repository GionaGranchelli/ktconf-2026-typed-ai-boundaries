package dev.giona.ktconf

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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowApprovalFlowTest {
    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `workflow assesses suspends notifies and resumes`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val pending = rest.exchange(
            "/workflow-demo/analyze",
            HttpMethod.POST,
            HttpEntity(DemoRequests.payment(), headers),
            WorkflowAwaitingApprovalView::class.java,
        )

        assertEquals(HttpStatus.ACCEPTED, pending.statusCode)
        assertEquals("amount-above-5000-eur", pending.body!!.approvalGate)
        assertEquals("RECORDED", pending.body!!.notificationStatus)
        assertTrue(pending.body!!.assessment.rationale.isNotBlank())
        assertEquals("REQUEST_HUMAN_APPROVAL", pending.body!!.assessment.recommendedAction.name)
        assertEquals(1, stats().emailNotificationCount)
        assertEquals(0, stats().paymentExecutionCount)

        val approved = rest.postForEntity(
            "/approvals/${pending.body!!.approvalId}/approve",
            null,
            InvoiceAssessment::class.java,
        )
        assertEquals(HttpStatus.OK, approved.statusCode)
        assertEquals(1, stats().paymentExecutionCount)
    }

    private fun stats(): StatsResponse =
        rest.getForEntity("/governance/stats", StatsResponse::class.java).body!!
}

data class WorkflowAwaitingApprovalView(
    val approvalId: String,
    val workflowRunId: String,
    val approvalGate: String,
    val assessment: InvoiceAssessment,
    val notificationStatus: String,
)
