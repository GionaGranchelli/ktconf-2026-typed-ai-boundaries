package dev.giona.ktconf.notifications

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

data class SendApprovalEmailInput(
    val to: String,
    val invoiceId: String,
    val approvalId: String,
)

data class SendApprovalEmailResult(
    val status: String,
)

/** Fake, deterministic email tool: records the message and performs no I/O. */
@Component
class SendApprovalEmailTool(
    private val email: FakeEmailService,
) : TramaiTool<SendApprovalEmailInput, SendApprovalEmailResult> {
    override val name: String = "send-approval-email"
    override val description: String = "Notify an approver that an invoice payment is awaiting review"
    override val inputType: KClass<SendApprovalEmailInput> = SendApprovalEmailInput::class
    override val idempotent: Boolean = true
    override val sideEffectLevel: SideEffectLevel = SideEffectLevel.WRITE
    override val security: ToolSecurityMetadata = ToolSecurityMetadata(
        permission = "notification.email.send",
        risk = RiskLevel.LOW,
        approval = ApprovalMode.AUTO,
        managedNetworkEgress = ManagedNetworkEgress.DENY,
        audit = AuditDetail.FULL,
    )

    fun send(input: SendApprovalEmailInput): SendApprovalEmailResult {
        email.sendApprovalRequest(input.to, input.invoiceId, input.approvalId)
        return SendApprovalEmailResult(status = "RECORDED")
    }

    override suspend fun execute(input: SendApprovalEmailInput, context: ToolExecutionContext): SendApprovalEmailResult =
        send(input)
}
