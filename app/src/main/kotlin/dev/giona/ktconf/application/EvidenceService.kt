package dev.giona.ktconf.application

import dev.tramai.security.audit.AuditChainVerifier
import dev.tramai.security.audit.AuditEvent
import dev.tramai.security.audit.AuditStore
import dev.tramai.security.audit.toCanonicalJson
import dev.tramai.sovereign.SovereignTramai
import dev.tramai.sovereign.evidence.AuditChainEvidenceV1
import dev.tramai.sovereign.evidence.SovereignEvidencePackWriter
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Per-workflow evidence: resolves the workflow run through the approval
 * registry and returns ONLY that execution's audit records — never the
 * global store. Files are named by approval id so concurrent workflows do
 * not overwrite each other.
 */
@Service
class EvidenceService(
    private val registry: PendingApprovalRegistry,
    private val auditStore: AuditStore,
    private val tramai: SovereignTramai,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun evidenceFor(approvalId: String): EvidenceResult {
        val pending = registry.require(approvalId)
        val events = auditStore.readStream(pending.workflowRunId)
        check(events.isNotEmpty()) { "no audit events recorded for approval $approvalId" }
        val chain = AuditChainVerifier.verify(events)
        check(chain.isValid) { "audit chain must verify for approval $approvalId" }

        val outputDir = Path.of(".build", "demo", "evidence")
        Files.createDirectories(outputDir)
        writeAuditChain(outputDir.resolve("audit-chain-$approvalId.json"), events)
        val evidencePack = tramai.evidencePack(
            auditChain = AuditChainEvidenceV1(
                isValid = chain.isValid,
                totalEvents = events.size,
            ),
        )
        val packPath = outputDir.resolve("sovereign-evidence-pack-$approvalId.json")
        SovereignEvidencePackWriter.write(evidencePack, packPath)
        log.info("Evidence written: approvalId={}, events={}, auditChain={}, evidenceDirectory={}", approvalId, events.size, chain.isValid, outputDir.toAbsolutePath().normalize())

        return EvidenceResult(
            auditEvents = events,
            chainValid = chain.isValid,
            eventCount = events.size,
            evidenceDirectory = outputDir.toAbsolutePath().normalize(),
        )
    }

    private fun writeAuditChain(path: Path, events: List<AuditEvent>) {
        Files.writeString(
            path,
            buildString {
                appendLine("[")
                events.forEachIndexed { index, event ->
                    append(event.toCanonicalJson().prependIndent("  "))
                    if (index != events.lastIndex) append(",")
                    appendLine()
                }
                appendLine("]")
            },
        )
    }
}

data class EvidenceResult(
    val auditEvents: List<AuditEvent>,
    val chainValid: Boolean,
    val eventCount: Int,
    val evidenceDirectory: Path,
)
