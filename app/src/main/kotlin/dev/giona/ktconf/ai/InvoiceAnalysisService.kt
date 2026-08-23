package dev.giona.ktconf.ai

import dev.tramai.core.annotations.AiService
import dev.tramai.core.annotations.Operation
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.model.ClassifiedDocument

/**
 * The typed AI boundary — the ONE contract the audience should remember.
 *
 * Two governed operations on one service:
 * - [analyzeLocal] runs on `local-invoice-model` (LOCAL provider) and may
 *   request the HIGH-risk `schedule-payment` tool.
 * - [analyzeCloud] runs on `cloud-invoice-model` (GLOBAL_CLOUD provider)
 *   and has no tools.
 *
 * The application chooses which operation to call. TramAI validates whether
 * that route is allowed for the document's classification.
 */
@AiService
interface InvoiceAnalysisService {

    @Operation(
        prompt = "Analyze the invoice document and return a structured InvoiceAssessment.",
        model = "local-invoice-model",
        tools = ["schedule-payment"],
    )
    suspend fun analyzeLocal(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = "Analyze the invoice document and return a structured InvoiceAssessment.",
        model = "cloud-invoice-model",
    )
    suspend fun analyzeCloud(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment
}
