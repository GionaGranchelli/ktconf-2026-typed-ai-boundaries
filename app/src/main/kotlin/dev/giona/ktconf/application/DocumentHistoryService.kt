package dev.giona.ktconf.application

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.pdf.TrustedPdfMetadata
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.exception.PolicyViolationException
import dev.tramai.security.audit.AuditEvent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

/** Demo-scoped backend history. It is reset when the application restarts. */
@Service
class DocumentHistoryService {
    private val records = ConcurrentHashMap<String, DocumentHistoryRecord>()

    fun record(
        invoice: InvoiceDocument,
        metadata: TrustedPdfMetadata,
        outcome: AnalyzeOutcome,
    ): DocumentHistoryRecord {
        val record = when (outcome) {
            is AnalyzeOutcome.Typed -> DocumentHistoryRecord(
                id = "document-${UUID.randomUUID()}",
                recordedAt = Instant.now(),
                invoice = invoice,
                metadata = metadata,
                selectedRoute = outcome.selectedRoute,
                classificationSource = outcome.classificationSource,
                status = "COMPLETED",
                assessment = outcome.assessment,
                workflowEvents = listOf(
                    HistoryEvent("DOCUMENT_UPLOADED", "Document uploaded", "Trusted PDF metadata accepted", Instant.now()),
                    HistoryEvent("AUTO_APPROVED", "Automatically approved", "${outcome.assessment.risk} risk; no human approval required", Instant.now()),
                ),
            )
            is AnalyzeOutcome.AwaitingApproval -> DocumentHistoryRecord(
                id = "document-${UUID.randomUUID()}",
                recordedAt = Instant.now(),
                invoice = invoice,
                metadata = metadata,
                selectedRoute = outcome.selectedRoute,
                classificationSource = outcome.classificationSource,
                status = "AWAITING_APPROVAL",
                approvalId = outcome.approvalId,
                workflowRunId = outcome.workflowRunId,
                toolName = outcome.toolName,
                rationale = outcome.rationale,
                workflowEvents = listOf(
                    HistoryEvent("DOCUMENT_UPLOADED", "Document uploaded", "Trusted PDF metadata accepted", Instant.now()),
                    HistoryEvent("APPROVAL_REQUIRED", "Human approval required", "${outcome.toolName} requested by the model", Instant.now()),
                ),
            )
        }
        records[record.id] = record
        if (record.approvalId != null) approvalIndex[record.approvalId] = record.id
        return record
    }

    fun list(): List<DocumentHistoryRecord> = records.values.sortedByDescending { it.recordedAt }

    fun get(id: String): DocumentHistoryRecord = records[id]
        ?: throw NoSuchElementException("document history record not found: $id")

    fun updateApproval(approvalId: String, status: String, assessment: InvoiceAssessment? = null) {
        val id = approvalIndex[approvalId] ?: return
        records.computeIfPresent(id) { _, record ->
            record.copy(
                status = status,
                assessment = assessment,
                workflowEvents = record.workflowEvents + HistoryEvent(
                    if (status == "SCHEDULED") "PAYMENT_SCHEDULED" else "APPROVAL_DENIED",
                    if (status == "SCHEDULED") "Payment scheduled" else "Approval denied",
                    if (status == "SCHEDULED") "Payment executed exactly once" else "No payment side effect occurred",
                    Instant.now(),
                ),
            )
        }
    }

    fun attachEvidence(approvalId: String, events: List<AuditEvent>, chainValid: Boolean) {
        val id = approvalIndex[approvalId] ?: return
        records.computeIfPresent(id) { _, record ->
            record.copy(auditEvents = events, auditChainValid = chainValid)
        }
    }

    fun recordRejected(
        invoice: InvoiceDocument,
        metadata: TrustedPdfMetadata,
        selectedRoute: InvoiceRoute,
        error: PolicyViolationException,
    ): DocumentHistoryRecord {
        val record = DocumentHistoryRecord(
            id = "document-${UUID.randomUUID()}",
            recordedAt = Instant.now(),
            invoice = invoice,
            metadata = metadata,
            selectedRoute = selectedRoute,
            classificationSource = ClassificationSource.RULE_BASED,
            status = "DENIED",
            denialReasonCode = error.decision.reasonCode,
            rationale = error.decision.reason,
            workflowEvents = listOf(
                HistoryEvent("DOCUMENT_UPLOADED", "Document uploaded", "Trusted PDF metadata accepted", Instant.now()),
                HistoryEvent("POLICY_DENIED", "Denied by TramAI", "${error.decision.reasonCode}; provider was not invoked", Instant.now()),
            ),
        )
        records[record.id] = record
        return record
    }

    private val approvalIndex = ConcurrentHashMap<String, String>()
}

data class DocumentHistoryRecord(
    val id: String,
    val recordedAt: Instant,
    val invoice: InvoiceDocument,
    val metadata: TrustedPdfMetadata,
    val selectedRoute: InvoiceRoute,
    val classificationSource: ClassificationSource,
    val status: String,
    val assessment: InvoiceAssessment? = null,
    val approvalId: String? = null,
    val workflowRunId: String? = null,
    val toolName: String? = null,
    val rationale: String? = null,
    val denialReasonCode: String? = null,
    val workflowEvents: List<HistoryEvent> = emptyList(),
    val auditEvents: List<AuditEvent> = emptyList(),
    val auditChainValid: Boolean? = null,
)

data class HistoryEvent(
    val type: String,
    val label: String,
    val detail: String,
    val timestamp: Instant,
)
