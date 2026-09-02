package dev.giona.ktconf.payments

import dev.giona.ktconf.domain.SchedulePaymentInput
import dev.giona.ktconf.domain.SchedulePaymentResult
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)
    private val executions = ConcurrentHashMap<String, SchedulePaymentResult>()

    fun scheduleExactlyOnce(
        idempotencyKey: String,
        input: SchedulePaymentInput,
    ): SchedulePaymentResult {
        val newResult = SchedulePaymentResult(
                paymentReference = "payment-${input.invoiceId}",
                status = "SCHEDULED",
            )
        val existing = executions.putIfAbsent(idempotencyKey, newResult)
        val result = existing ?: newResult
        log.info("Payment scheduling {}: invoiceId={}, executionCount={}", if (existing == null) "executed" else "deduplicated", input.invoiceId, executions.size)
        return result
    }

    fun executionCount(): Int = executions.size

    fun hasExecutionForInvoice(invoiceId: String): Boolean =
        executions.values.any { it.paymentReference == "payment-$invoiceId" }
}
