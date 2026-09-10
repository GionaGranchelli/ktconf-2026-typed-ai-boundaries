package dev.giona.ktconf

import dev.giona.ktconf.observability.DlpAppliedRule
import dev.giona.ktconf.observability.DlpRedactionEvidenceStore
import dev.tramai.core.policy.ClassificationSource
import dev.tramai.core.policy.DataClassification
import dev.tramai.core.security.DlpContentLocation
import dev.tramai.core.security.DlpContentType
import dev.tramai.core.security.DlpContext
import dev.tramai.core.security.DlpRedaction
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DlpRedactionEvidenceStoreTest {

    @Test
    fun `capture keeps concurrent DLP events bound to the correct request`() = runBlocking {
        val store = DlpRedactionEvidenceStore()

        val first = async {
            store.capture {
                delay(50)
                store.record(
                    context(correlationId = "corr-a", providerId = "provider-a", modelName = "model-a"),
                    listOf(DlpRedaction("email", 1)),
                )
                "first"
            }
        }
        val second = async {
            store.capture {
                store.record(
                    context(correlationId = "corr-b", providerId = "provider-b", modelName = "model-b"),
                    listOf(DlpRedaction("iban", 1)),
                )
                "second"
            }
        }

        val firstResult = first.await()
        val secondResult = second.await()

        assertEquals("first", firstResult.value)
        assertEquals("corr-a", firstResult.summary.correlationId)
        assertEquals("provider-a", firstResult.summary.providerId)
        assertEquals("model-a", firstResult.summary.modelName)
        assertEquals(listOf(DlpAppliedRule("email", 1)), firstResult.summary.appliedRules)

        assertEquals("second", secondResult.value)
        assertEquals("corr-b", secondResult.summary.correlationId)
        assertEquals("provider-b", secondResult.summary.providerId)
        assertEquals("model-b", secondResult.summary.modelName)
        assertEquals(listOf(DlpAppliedRule("iban", 1)), secondResult.summary.appliedRules)
    }

    private fun context(
        correlationId: String,
        providerId: String,
        modelName: String,
    ) = DlpContext(
        contentType = DlpContentType.MODEL_OUTPUT,
        contentLocation = DlpContentLocation.MODEL_RESPONSE_CONTENT,
        operationInterface = "dev.giona.ktconf.ai.DocumentAnalysisAi",
        operationMethod = "analyzeDocument",
        providerId = providerId,
        modelName = modelName,
        correlationId = correlationId,
        dataClassification = DataClassification.CONFIDENTIAL,
        classificationSource = ClassificationSource.RULE_BASED,
    )
}
