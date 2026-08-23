package dev.giona.ktconf.payments

import dev.giona.ktconf.domain.SchedulePaymentInput
import dev.giona.ktconf.domain.SchedulePaymentResult
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * In-memory payment ledger with exactly-once idempotency.
 *
 * Deduplicates on the engine-supplied [dev.tramai.core.model.ToolExecutionContext.idempotencyKey],
 * so approval resume cannot double-schedule a payment within this demo's
 * single-process ledger (same idempotency key → one entry).
 */
@Component
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
