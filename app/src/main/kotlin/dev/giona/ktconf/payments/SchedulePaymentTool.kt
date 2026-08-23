package dev.giona.ktconf.payments

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
import kotlin.reflect.KClass
import org.springframework.stereotype.Component

/**
 * HIGH-risk payment tool that requires human approval before execution.
 *
 * This is a normal Spring bean implementing [TramaiTool]. The sovereign
 * starter collects it automatically (upstream tramAI PR #268) — the
 * conference application does NOT register it with the runtime manually.
 *
 * The security metadata is part of the TOOL, not of conference code:
 * - permission = payment.schedule
 * - risk = HIGH  (sovereign policy: approvals required for HIGH+)
 * - approval = HUMAN_REQUIRED
 * - side effect = WRITE
 * - managed network egress = DENY
 * - audit = FULL
 */
@Component
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
