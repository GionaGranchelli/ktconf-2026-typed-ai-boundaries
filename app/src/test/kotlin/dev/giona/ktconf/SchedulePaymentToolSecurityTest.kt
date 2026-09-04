package dev.giona.ktconf

import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.giona.ktconf.payments.AutoSchedulePaymentTool
import dev.giona.ktconf.payments.SchedulePaymentTool
import dev.tramai.core.model.SideEffectLevel
import dev.tramai.core.policy.ApprovalMode
import dev.tramai.core.policy.AuditDetail
import dev.tramai.core.policy.ManagedNetworkEgress
import dev.tramai.core.policy.RiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tool security: the payment capability's metadata is declared on the TOOL
 * (not in conference code) — the sovereign profile policy then enforces it.
 * Lock the exact declaration so the demo's governance claim is testable.
 */
class SchedulePaymentToolSecurityTest {

    private val tool = SchedulePaymentTool(InMemoryPaymentLedger())

    @Test
    fun `security metadata locks the payment capability`() {
        assertEquals("schedule-payment", tool.name)
        assertTrue(tool.idempotent)
        assertEquals(SideEffectLevel.WRITE, tool.sideEffectLevel)

        val security = assertNotNull(tool.security)
        assertEquals("payment.schedule", security.permission)
        assertEquals(RiskLevel.HIGH, security.risk)
        assertEquals(ApprovalMode.HUMAN_REQUIRED, security.approval)
        assertEquals(ManagedNetworkEgress.DENY, security.managedNetworkEgress)
        assertEquals(AuditDetail.FULL, security.audit)
    }

    @Test
    fun `low-value auto payment has explicit low auto policy`() {
        val tool = AutoSchedulePaymentTool(InMemoryPaymentLedger())
        assertEquals("auto-schedule-payment", tool.name)
        assertTrue(tool.idempotent)
        assertEquals(SideEffectLevel.WRITE, tool.sideEffectLevel)

        val security = assertNotNull(tool.security)
        assertEquals("payment.schedule", security.permission)
        assertEquals(RiskLevel.LOW, security.risk)
        assertEquals(ApprovalMode.AUTO, security.approval)
        assertEquals(ManagedNetworkEgress.DENY, security.managedNetworkEgress)
        assertEquals(AuditDetail.FULL, security.audit)
    }
}
