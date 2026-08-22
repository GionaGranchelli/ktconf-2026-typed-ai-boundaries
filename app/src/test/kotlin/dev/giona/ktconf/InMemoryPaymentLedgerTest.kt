package dev.giona.ktconf

import dev.giona.ktconf.domain.SchedulePaymentInput
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ledger idempotency, tested directly: the approval tests prove the engine
 * rejects a duplicate continuation, but the ledger's own defense (dedupe on
 * the engine-supplied idempotency key) deserves an independent test —
 * the docs claim both protections.
 */
class InMemoryPaymentLedgerTest {

    @Test
    fun `same idempotency key schedules exactly once`() {
        val ledger = InMemoryPaymentLedger()
        val input = SchedulePaymentInput(
            invoiceId = "KTCONF-PAY-001",
            amountCents = 1_840_000,
            currency = "EUR",
        )

        val first = ledger.scheduleExactlyOnce("key-1", input)
        val second = ledger.scheduleExactlyOnce("key-1", input)

        assertTrue(first === second, "same idempotency key must return the same execution")
        assertEquals(1, ledger.executionCount())
    }

    @Test
    fun `distinct keys schedule distinct executions`() {
        val ledger = InMemoryPaymentLedger()
        val input = SchedulePaymentInput(
            invoiceId = "KTCONF-PAY-001",
            amountCents = 1_840_000,
            currency = "EUR",
        )

        ledger.scheduleExactlyOnce("key-1", input)
        ledger.scheduleExactlyOnce("key-2", input)

        assertEquals(2, ledger.executionCount())
    }
}
