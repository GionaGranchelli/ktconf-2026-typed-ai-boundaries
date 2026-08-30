package dev.giona.ktconf.application

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.SchedulePaymentInput
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.tramai.core.approval.gateway.ApprovalGateway
import dev.tramai.core.approval.gateway.ApprovalId
import dev.tramai.core.approval.gateway.ApprovalRecommendation
import dev.tramai.core.approval.gateway.ApprovalRequestResult
import dev.tramai.core.approval.gateway.ApprovalSubject
import dev.tramai.core.approval.gateway.ApproverRole
import dev.tramai.core.approval.gateway.AuditStreamId
import dev.tramai.core.approval.gateway.ResumeToken
import dev.tramai.core.approval.gateway.WorkflowRunId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * In-memory adapter for TramAI's workflow-oriented [ApprovalGateway] contract.
 *
 * The trusted amount rule calls this gateway; the model never decides whether
 * approval is required. Payment is scheduled exactly once only after a human
 * decision reaches [approve]. This adapter is intentionally process-local for
 * the conference demo; a production deployment would use a durable gateway.
 */
@Component
class WorkflowHumanApprovalGateway(
    private val ledger: InMemoryPaymentLedger,
) : ApprovalGateway {
    private val log = LoggerFactory.getLogger(javaClass)
    private val records = ConcurrentHashMap<String, Record>()

    enum class Status { PENDING, APPROVED, DENIED }

    private data class Record(
        val workflowRunId: String,
        val recommendation: ApprovalRecommendation,
        val assessment: InvoiceAssessment? = null,
        val status: Status = Status.PENDING,
    )

    suspend fun requestPaymentApproval(
        assessment: InvoiceAssessment,
        workflowRunId: String,
    ): ApprovalRequestResult.Suspended {
        val result = requestApproval(
            subject = ApprovalSubject(assessment.invoiceId),
            recommendation = ApprovalRecommendation(
                type = APPROVAL_GATE,
                summary = assessment.rationale,
                payload = mapOf(
                    "amountCents" to assessment.amountCents.toString(),
                    "currency" to assessment.currency,
                ),
            ),
            requiredRole = ApproverRole("payment-approver"),
            workflowRunId = WorkflowRunId(workflowRunId),
        ) as ApprovalRequestResult.Suspended
        records.computeIfPresent(result.approvalId.value) { _, record ->
            record.copy(assessment = assessment)
        }
        return result
    }

    override suspend fun requestApproval(
        subject: ApprovalSubject,
        recommendation: ApprovalRecommendation,
        requiredRole: ApproverRole,
        workflowRunId: WorkflowRunId?,
    ): ApprovalRequestResult {
        val runId = workflowRunId?.value ?: "invoice-${subject.value}-${UUID.randomUUID()}"
        val approvalId = "approval-${UUID.randomUUID()}"
        records[approvalId] = Record(runId, recommendation)
        log.info(
            "Human approval requested: approvalId={}, workflowRunId={}, gate={}, role={}",
            approvalId,
            runId,
            recommendation.type,
            requiredRole.value,
        )
        return ApprovalRequestResult.Suspended(
            approvalId = ApprovalId(approvalId),
            workflowRunId = WorkflowRunId(runId),
            auditStreamId = AuditStreamId(runId),
            resumeToken = ResumeToken(UUID.randomUUID().toString()),
        )
    }

    fun contains(approvalId: String): Boolean = records.containsKey(approvalId)

    @Synchronized
    fun approve(approvalId: String): InvoiceAssessment {
        val record = requireRecord(approvalId)
        if (record.status != Status.PENDING) {
            throw WorkflowApprovalStateException("workflow approval '$approvalId' is ${record.status}")
        }
        val assessment = requireNotNull(record.assessment) { "workflow approval has no assessment" }
        ledger.scheduleExactlyOnce(
            idempotencyKey = "workflow-approval:$approvalId",
            input = SchedulePaymentInput(
                invoiceId = assessment.invoiceId,
                amountCents = assessment.amountCents,
                currency = assessment.currency,
            ),
        )
        records[approvalId] = record.copy(status = Status.APPROVED)
        log.info("Human approval completed; payment scheduled: approvalId={}, invoiceId={}", approvalId, assessment.invoiceId)
        return assessment
    }

    @Synchronized
    fun deny(approvalId: String) {
        val record = requireRecord(approvalId)
        if (record.status != Status.PENDING) {
            throw WorkflowApprovalStateException("workflow approval '$approvalId' is ${record.status}")
        }
        records[approvalId] = record.copy(status = Status.DENIED)
        log.info("Human approval denied; payment not scheduled: approvalId={}", approvalId)
    }

    private fun requireRecord(approvalId: String): Record =
        records[approvalId] ?: throw ApprovalNotFoundException(approvalId)

    companion object {
        const val APPROVAL_GATE = "amount-above-5000-eur"
    }
}

class WorkflowApprovalStateException(message: String) : RuntimeException(message)
