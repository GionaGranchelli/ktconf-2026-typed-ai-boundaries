package dev.giona.ktconf.observability

import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification
import dev.tramai.core.security.DlpContentType
import dev.tramai.core.security.DlpContext
import dev.tramai.core.security.DlpRedaction
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component

@Component
class DlpRedactionEvidenceStore {
    private val sequence = AtomicLong(0)
    private val captures = ConcurrentHashMap<String, CopyOnWriteArrayList<DlpRedactionRecord>>()
    private val currentCaptureId = ThreadLocal<String?>()

    fun record(
        context: DlpContext,
        redactions: List<DlpRedaction>,
    ) {
        val captureId = currentCaptureId.get() ?: return
        val bucket = captures.computeIfAbsent(captureId) { CopyOnWriteArrayList() }
        redactions.forEach { redaction ->
            bucket += DlpRedactionRecord(
                sequence = sequence.incrementAndGet(),
                recordedAt = Instant.now(),
                context = context,
                ruleId = redaction.ruleId,
                replacementCount = redaction.replacementCount,
            )
        }
    }

    suspend fun <T> capture(
        block: suspend () -> T,
    ): CapturedDlpExecution<T> {
        val captureId = UUID.randomUUID().toString()
        return try {
            val value = withContext(currentCaptureId.asContextElement(captureId)) { block() }
            val matching = captures.remove(captureId).orEmpty()
            CapturedDlpExecution(value = value, summary = summarize(matching))
        } finally {
            captures.remove(captureId)
        }
    }

    private fun summarize(
        records: List<DlpRedactionRecord>,
    ): DlpRedactionSummary {
        val matching = records
            .filter { it.context.contentType == DlpContentType.MODEL_OUTPUT }
            .sortedBy { it.sequence }
        require(matching.isNotEmpty()) {
            "No captured DLP redaction evidence was recorded"
        }

        val first = matching.first()
        val grouped = matching
            .groupBy { it.ruleId }
            .toSortedMap()
            .map { (ruleId, entries) ->
                DlpAppliedRule(
                    ruleId = ruleId,
                    replacementCount = entries.sumOf { it.replacementCount },
                )
            }

        return DlpRedactionSummary(
            correlationId = first.context.correlationId,
            operationInterface = first.context.operationInterface,
            operationMethod = first.context.operationMethod,
            providerId = first.context.providerId,
            modelName = first.context.modelName,
            contentType = first.context.contentType,
            dataClassification = first.context.dataClassification,
            classificationSource = first.context.classificationSource,
            replacementCount = grouped.sumOf { it.replacementCount },
            appliedRules = grouped,
        )
    }
}

data class CapturedDlpExecution<T>(
    val value: T,
    val summary: DlpRedactionSummary,
)

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
    val contentType: DlpContentType,
    val dataClassification: DataClassification?,
    val classificationSource: ClassificationSource?,
    val replacementCount: Int,
    val appliedRules: List<DlpAppliedRule>,
)

data class DlpAppliedRule(
    val ruleId: String,
    val replacementCount: Int,
)
