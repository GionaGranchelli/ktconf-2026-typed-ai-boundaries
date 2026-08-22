package dev.giona.ktconf

import dev.giona.ktconf.scenarios.ApprovalDecision
import dev.giona.ktconf.scenarios.ApprovalScenario
import dev.giona.ktconf.scenarios.ApprovalResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Approval: the HIGH-risk tool request suspends before execution and
 * only resumes through TramAI's real approval/continuation mechanism.
 */
class ApprovalScenarioTest {

    @Test
    fun `tool request suspends before execution`(): Unit = runBlocking {
        val result = ApprovalScenario().run(ApprovalDecision.APPROVE)

        assertEquals(0, result.ledgerBeforeDecision)
        assertNotNull(result.suspension.approvalId)
    }

    @Test
    fun `approved resume executes exactly once`(): Unit = runBlocking {
        val result: ApprovalResult = ApprovalScenario().run(ApprovalDecision.APPROVE)

        assertEquals(1, result.ledgerAfterDecision)
        assertNotNull(result.assessment)
    }

    @Test
    fun `duplicate resume does not double-execute`(): Unit = runBlocking {
        val result = ApprovalScenario().run(ApprovalDecision.APPROVE)

        assertTrue(result.resumeFailure, "second resume must be rejected")
        assertEquals(1, result.ledgerAfterDuplicateResume)
    }

    @Test
    fun `denial keeps ledger at zero`(): Unit = runBlocking {
        val result = ApprovalScenario().run(ApprovalDecision.DENY)

        assertTrue(result.resumeFailure)
        assertEquals(0, result.ledgerAfterDecision)
    }
}
