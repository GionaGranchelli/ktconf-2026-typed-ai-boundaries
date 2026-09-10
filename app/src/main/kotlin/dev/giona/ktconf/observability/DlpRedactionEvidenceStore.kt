package dev.giona.ktconf.observability

import dev.tramai.core.security.DlpContentType
import dev.tramai.core.security.DlpContext
import dev.tramai.core.security.DlpRedaction
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.springframework.stereotype.Component

@Component
class DlpRedactionEvidenceStore {
    private val sequence = AtomicLong(0)
    private val records = CopyOnWriteArrayList<DlpRedactionRecord>()

    fun checkpoint(): Long = sequence.get()

    fun record(
        context: DlpContext,
        redactions: List<DlpRedaction>,
    ) {
        redactions.forEach { redaction ->
            records += DlpRedactionRecord(
                sequence = sequence.incrementAndGet(),
                recordedAt = Instant.now(),
                context = context,
                ruleId = redaction.ruleId,
                replacementCount = redaction.replacementCount,
            )
        }
    }

    fun summarySince(
        checkpoint: Long,
        operationMethod: String,
    ): DlpRedactionSummary {
        val matching = records
            .filter {
                it.sequence > checkpoint &&
                    it.context.contentType == DlpContentType.MODEL_OUTPUT &&
                    it.context.operationMethod == operationMethod
            }
            .sortedBy { it.sequence }
        require(matching.isNotEmpty()) {
            "No DLP redaction evidence was recorded for $operationMethod"
        }

        val first = matching.first()
        val correlationId = first.context.correlationId
        val grouped = matching
            .filter { it.context.correlationId == correlationId }
            .groupBy { it.ruleId }
            .toSortedMap()
            .map { (ruleId, entries) ->
                DlpAppliedRule(
                    ruleId = ruleId,
                    replacementCount = entries.sumOf { it.replacementCount },
                )
            }

        return DlpRedactionSummary(
            correlationId = correlationId,
            operationInterface = first.context.operationInterface,
            operationMethod = first.context.operationMethod,
            providerId = first.context.providerId,
            modelName = first.context.modelName,
            replacementCount = grouped.sumOf { it.replacementCount },
            appliedRules = grouped,
        )
    }

    fun latestSummary(
        operationMethod: String,
    ): DlpRedactionSummary? {
        val checkpoint = (records.maxOfOrNull { it.sequence } ?: return null) - 100
        return runCatching { summarySince(checkpoint, operationMethod) }.getOrNull()
    }
}

data class DlpRedactionRecord(
    val sequence: Long,
    val recordedAt: Instant,
    val context: DlpContext,
    val ruleId: String,
    val replacementCount: Int,
)

data class DlpRedactionSummary(
    val correlationId: String,
    val operationInterface: String,
    val operationMethod: String,
    val providerId: String?,
    val modelName: String?,
    val replacementCount: Int,
    val appliedRules: List<DlpAppliedRule>,
)

data class DlpAppliedRule(
    val ruleId: String,
    val replacementCount: Int,
)
