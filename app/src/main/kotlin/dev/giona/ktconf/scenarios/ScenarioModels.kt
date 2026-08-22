package dev.giona.ktconf.scenarios

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.exception.PolicyViolationException
import dev.tramai.core.exception.StructuredOutputException
import dev.tramai.security.audit.AuditEvent
import java.nio.file.Path

/**
 * Results of each demo scenario. Scenarios assert the expected TramAI
 * behavior internally (fail fast on stage); tests assert on the data.
 */
sealed interface ScenarioResult

/** Scenario 1 — typed boundary: model output becomes a typed result. */
data class TypedBoundaryResult(
    val assessment: InvoiceAssessment,
) : ScenarioResult

/** Scenario 2 — broken model: real structured-output rejection, zero side effects. */
data class InvalidOutputResult(
    val failure: StructuredOutputException,
    val paymentExecutionCount: Int,
) : ScenarioResult

/** Scenario 3 — restricted data: cloud denied before invocation, LOCAL allowed. */
data class RestrictedDataResult(
    val denial: PolicyViolationException,
    val cloudInvocationCount: Int,
    val localAssessment: InvoiceAssessment,
) : ScenarioResult

/** Scenario 4/5 — approval lifecycle. */
data class ApprovalResult(
    val suspension: ApprovalSuspendedException,
    val ledgerBeforeDecision: Int,
    val decision: ApprovalDecision,
    val ledgerAfterDecision: Int,
    val ledgerAfterDuplicateResume: Int,
    val assessment: InvoiceAssessment?,
    val resumeFailure: Boolean,
) : ScenarioResult

/** Scenario 6 — evidence: real audit records, verified chain, written artifacts. */
data class EvidenceResult(
    val auditEvents: List<AuditEvent>,
    val chainValid: Boolean,
    val eventCount: Int,
    val evidenceDirectory: Path,
) : ScenarioResult

enum class ApprovalDecision {
    APPROVE,
    DENY,
    ABORT,
}
