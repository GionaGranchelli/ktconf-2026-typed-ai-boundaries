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
        reissuedFromApprovalId: String? = null,
    ): DocumentHistoryRecord {
        val record = when (outcome) {
            is AnalyzeOutcome.Typed -> {
                val reviewRequired = outcome.assessment.risk == dev.giona.ktconf.domain.InvoiceRisk.HIGH
                val paymentScheduled = outcome.paymentScheduled
                DocumentHistoryRecord(
                    id = "document-${UUID.randomUUID()}",
                    recordedAt = Instant.now(),
                    invoice = invoice,
                    metadata = metadata,
                    selectedRoute = outcome.selectedRoute,
                    classificationSource = outcome.classificationSource,
                    status = when {
                        paymentScheduled -> "SCHEDULED"
                        reviewRequired -> "REVIEW_REQUIRED"
                        else -> "AUTO_PAYMENT_PENDING"
                    },
                    assessment = outcome.assessment,
                    reissuedFromApprovalId = reissuedFromApprovalId,
                    workflowEvents = listOf(
                        HistoryEvent("DOCUMENT_UPLOADED", "Document uploaded", "Trusted PDF metadata accepted", Instant.now()),
                        *reissueEvents(reissuedFromApprovalId),
                        if (paymentScheduled) {
                            HistoryEvent(
                                "PAYMENT_AUTO_SCHEDULED",
                                "Payment scheduled automatically",
                                "LOW-risk auto-schedule-payment executed exactly once under the trusted amount rule",
                                Instant.now(),
                            )
                        } else if (reviewRequired) {
                            HistoryEvent(
                                "HUMAN_REVIEW_REQUIRED",
                                "Human review required",
                                "HIGH-risk assessment; no payment was executed on this analysis route",
                                Instant.now(),
                            )
                        } else {
                            HistoryEvent(
                                "AUTO_PAYMENT_PENDING",
                                "Automatic payment pending",
                                "LOW risk was established, but the auto-schedule-payment tool did not execute",
                                Instant.now(),
                            )
                        },
                    ),
                )
            }
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
                notificationStatus = outcome.notificationStatus,
                notificationRecipient = outcome.notificationRecipient,
                notificationSubject = outcome.notificationSubject,
                approvalExpiresAt = outcome.approvalExpiresAt,
                reissuedFromApprovalId = reissuedFromApprovalId,
                workflowEvents = listOf(
                    HistoryEvent("DOCUMENT_UPLOADED", "Document uploaded", "Trusted PDF metadata accepted", Instant.now()),
                    *reissueEvents(reissuedFromApprovalId),
                    HistoryEvent("APPROVAL_REQUIRED", "Human approval required", "${outcome.toolName} requested by the model", Instant.now()),
                    HistoryEvent("APPROVAL_EMAIL_RECORDED", "Approval email recorded", "${outcome.notificationSubject} → ${outcome.notificationRecipient}; fake email sink, no SMTP/network egress", Instant.now()),
                ),
            )
        }
        records[record.id] = record
        if (record.approvalId != null) approvalIndex[record.approvalId] = record.id
        return record
    }

    private fun reissueEvents(previousApprovalId: String?): Array<HistoryEvent> =
        if (previousApprovalId == null) emptyArray() else arrayOf(
            HistoryEvent(
                "REISSUE_CREATED",
                "Fresh approval created",
                "Reissued from expired approval $previousApprovalId",
                Instant.now(),
            ),
        )

    fun list(): List<DocumentHistoryRecord> = records.values.sortedByDescending { it.recordedAt }

    fun get(id: String): DocumentHistoryRecord = records[id]
        ?: throw NoSuchElementException("document history record not found: $id")

    fun findByApprovalId(approvalId: String): DocumentHistoryRecord? =
        approvalIndex[approvalId]?.let(records::get)

    fun markExpired(approvalId: String) {
        val id = approvalIndex[approvalId] ?: return
        records.computeIfPresent(id) { _, record ->
            record.copy(
                status = "EXPIRED",
                workflowEvents = record.workflowEvents + HistoryEvent(
                    "APPROVAL_EXPIRED",
                    "Approval expired",
                    "TramAI timed out the approval; the old continuation cannot be resumed",
                    Instant.now(),
                ),
            )
        }
    }

    fun linkReissue(oldApprovalId: String, newRecordId: String, newApprovalId: String?) {
        val oldId = approvalIndex[oldApprovalId] ?: return
        records.computeIfPresent(oldId) { _, record ->
            record.copy(
                reissuedToDocumentId = newRecordId,
                reissuedToApprovalId = newApprovalId,
                workflowEvents = record.workflowEvents + HistoryEvent(
                    "APPROVAL_REISSUED",
                    "Approval reissued",
                    "Fresh workflow created as document $newRecordId",
                    Instant.now(),
                ),
            )
        }
    }

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
    val notificationStatus: String? = null,
    val notificationRecipient: String? = null,
    val notificationSubject: String? = null,
    val approvalExpiresAt: Instant? = null,
    val reissuedFromApprovalId: String? = null,
    val reissuedToDocumentId: String? = null,
    val reissuedToApprovalId: String? = null,
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
