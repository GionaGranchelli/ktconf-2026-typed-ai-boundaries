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
 * Low-value payment path. The application selects this operation only after
 * the trusted invoice amount passes the automatic-payment threshold.
 */
@Component
class AutoSchedulePaymentTool(
    private val ledger: InMemoryPaymentLedger,
) : TramaiTool<SchedulePaymentInput, SchedulePaymentResult> {
    override val name: String = "auto-schedule-payment"
    override val description: String = "Automatically schedule a low-value payment"
    override val inputType: KClass<SchedulePaymentInput> = SchedulePaymentInput::class
    override val idempotent: Boolean = true
    override val sideEffectLevel: SideEffectLevel = SideEffectLevel.WRITE
    override val security: ToolSecurityMetadata = ToolSecurityMetadata(
        permission = "payment.schedule",
        risk = RiskLevel.LOW,
        approval = ApprovalMode.AUTO,
        managedNetworkEgress = ManagedNetworkEgress.DENY,
        audit = AuditDetail.FULL,
    )

    override suspend fun execute(
        input: SchedulePaymentInput,
        context: ToolExecutionContext,
    ): SchedulePaymentResult = ledger.scheduleExactlyOnce(
        idempotencyKey = context.idempotencyKey ?: "auto-payment:${input.invoiceId}",
        input = input,
    )
}
