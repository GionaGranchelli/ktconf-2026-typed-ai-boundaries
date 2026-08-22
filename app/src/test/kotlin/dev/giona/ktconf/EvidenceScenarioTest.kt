package dev.giona.ktconf

import dev.giona.ktconf.scenarios.EvidenceScenario
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
    }
}
