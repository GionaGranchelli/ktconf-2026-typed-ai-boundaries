package dev.giona.ktconf.tools

import dev.giona.ktconf.domain.SchedulePaymentInput
import dev.giona.ktconf.domain.SchedulePaymentResult
import dev.tramai.core.model.SideEffectLevel
import dev.tramai.core.model.ToolExecutionContext
import dev.tramai.core.model.TramaiTool
import dev.tramai.core.policy.ApprovalMode
import dev.tramai.core.policy.AuditDetail
import dev.tramai.core.policy.ManagedNetworkEgress
import dev.tramai.core.policy.RiskLevel
import dev.tramai.core.policy.ToolSecurityMetadata
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * In-memory payment ledger with exactly-once idempotency.
 *
 * Deduplicates on the engine-supplied [ToolExecutionContext.idempotencyKey],
 * so approval resume cannot double-schedule a payment within this demo's
 * single-process ledger (same idempotency key → one entry).
 */
class InMemoryPaymentLedger {
    private val executions = ConcurrentHashMap<String, SchedulePaymentResult>()

    fun scheduleExactlyOnce(
        idempotencyKey: String,
        input: SchedulePaymentInput,
    ): SchedulePaymentResult =
        executions.computeIfAbsent(idempotencyKey) {
            SchedulePaymentResult(
                paymentReference = "payment-${input.invoiceId}",
                status = "SCHEDULED",
            )
        }

    fun executionCount(): Int = executions.size
}

/**
 * HIGH-risk payment tool that requires human approval before execution.
 *
 * The security metadata is part of the TOOL, not of conference code:
 * - permission = payment.schedule
 * - risk = HIGH  (sovereign policy: approvals required for HIGH+)
 * - approval = HUMAN_REQUIRED
 * - side effect = WRITE
 * - managed network egress = DENY
 * - audit = FULL
 */
class SchedulePaymentTool(
    private val ledger: InMemoryPaymentLedger,
) : TramaiTool<SchedulePaymentInput, SchedulePaymentResult> {

    override val name: String = "schedule-payment"

    override val description: String = "Schedule a payment for an approved invoice"

    override val inputType: KClass<SchedulePaymentInput> = SchedulePaymentInput::class

    override val idempotent: Boolean = true

    override val sideEffectLevel: SideEffectLevel = SideEffectLevel.WRITE

    override val security: ToolSecurityMetadata? = ToolSecurityMetadata(
        permission = "payment.schedule",
        risk = RiskLevel.HIGH,
        approval = ApprovalMode.HUMAN_REQUIRED,
        managedNetworkEgress = ManagedNetworkEgress.DENY,
        audit = AuditDetail.FULL,
    )

    override suspend fun execute(
        input: SchedulePaymentInput,
        context: ToolExecutionContext,
    ): SchedulePaymentResult {
        val idempotencyKey = context.idempotencyKey
            ?: throw IllegalStateException("schedule-payment requires an idempotencyKey from the engine")
        return ledger.scheduleExactlyOnce(idempotencyKey, input)
    }
}
