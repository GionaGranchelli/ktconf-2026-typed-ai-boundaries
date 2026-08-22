package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.DemoResponses
import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.ai.ScriptedProvider
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.runtime.DemoRuntimeFactory
import dev.giona.ktconf.runtime.LOCAL_PROVIDER
import dev.giona.ktconf.runtime.local
import dev.tramai.core.approval.ApprovalTransition
import dev.tramai.engine.ResumeApprovalCommand
import dev.tramai.security.audit.AuditChainVerifier
import dev.tramai.security.audit.AuditEvent
import dev.tramai.security.audit.toCanonicalJson
import dev.tramai.sovereign.evidence.AuditChainEvidenceV1
import dev.tramai.sovereign.evidence.SovereignEvidencePackWriter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Scenario 6 — Evidence.
 *
 * Runs the approved payment flow, then reads the ACTUAL audit records
 * emitted by TramAI, verifies the hash chain, and writes two views:
 * raw machine-readable evidence under .build/demo/ and a presenter view.
 *
 * Never invents event rows — every row derives from a real audit record.
 */
class EvidenceScenario(
    private val factory: DemoRuntimeFactory = DemoRuntimeFactory(),
) {

    suspend fun run(): EvidenceResult {
        val outputDir = Path.of(".build", "demo", "evidence")
        Files.createDirectories(outputDir)

        factory.local(
            ScriptedProvider(LOCAL_PROVIDER, DemoResponses.paymentFlow),
        ).use { runtime ->
            val service = runtime.runtime.create(InvoiceAnalysisService::class)

            val suspension = try {
                service.analyze(DemoInvoices.paymentInvoice)
                error("Expected approval suspension")
            } catch (e: dev.tramai.core.exception.ApprovalSuspendedException) {
                e
            }

            val stored = runtime.approvalStore.get(suspension.approvalId) ?: error("missing approval")
            val approved = runtime.approvalStore.transition(
                suspension.approvalId,
                stored.version,
                ApprovalTransition.Approve(decidedBy = "demo-operator", comment = "Approved for evidence demo"),
            )
            runtime.runtime.resumeApprovalTyped<dev.giona.ktconf.domain.InvoiceAssessment>(
                ResumeApprovalCommand(
                    approvalId = suspension.approvalId,
                    approvalExpectedVersion = approved.version,
                    continuationExpectedVersion = suspension.continuationVersion,
                    presentedToken = suspension.challenge.token,
                    resumedBy = "demo-operator",
                ),
            )

            val events = runtime.auditStore.readStream(suspension.workflowRunId)
            check(events.isNotEmpty()) { "no audit events recorded" }
            val chain = AuditChainVerifier.verify(events)
            check(chain.isValid) { "audit chain must verify" }

            writeAuditChain(outputDir.resolve("audit-chain.json"), events)
            val evidencePack = runtime.tramai.evidencePack(
                auditChain = AuditChainEvidenceV1(
                    isValid = chain.isValid,
                    totalEvents = events.size,
                ),
            )
            val packPath = outputDir.resolve("sovereign-evidence-pack-v1.json")
            SovereignEvidencePackWriter.write(evidencePack, packPath)

            return EvidenceResult(
                auditEvents = events,
                chainValid = chain.isValid,
                eventCount = events.size,
                evidenceDirectory = outputDir.toAbsolutePath().normalize(),
            )
        }
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
