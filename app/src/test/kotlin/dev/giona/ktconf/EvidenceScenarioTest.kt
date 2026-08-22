package dev.giona.ktconf

import dev.giona.ktconf.scenarios.EvidenceScenario
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Evidence: real audit records exist, the hash chain verifies, and the
 * machine-readable evidence artifacts are actually written.
 */
class EvidenceScenarioTest {

    @Test
    fun `evidence is emitted and internally valid`() = runBlocking {
        val result = EvidenceScenario().run()

        assertTrue(result.eventCount > 0)
        assertTrue(result.chainValid)
        assertTrue(Files.exists(result.evidenceDirectory.resolve("audit-chain.json")))
        assertTrue(Files.exists(result.evidenceDirectory.resolve("sovereign-evidence-pack-v1.json")))

        // The stage story claims the exact governance timeline for one
        // approval execution: suspend → policy gate on resume → resume →
        // completion. Lock the exact ORDERED sequence, not set membership —
        // duplicates or reordering must fail this test.
        assertEquals(
            listOf(
                "APPROVAL_SUSPENDED",
                "BEFORE_WORKFLOW_RESUME",
                "APPROVAL_RESUMED",
                "APPROVAL_COMPLETED",
            ),
            result.auditEvents.map { it.enforcementPoint },
        )
    }
}
