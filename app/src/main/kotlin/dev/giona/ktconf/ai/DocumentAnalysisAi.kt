package dev.giona.ktconf.ai

import dev.giona.ktconf.domain.ConfidentialDocument
import dev.giona.ktconf.domain.DocumentAnalysis
import dev.tramai.core.annotations.AiService
import dev.tramai.core.annotations.Operation
import dev.tramai.core.model.ClassifiedDocument

@AiService
interface DocumentAnalysisAi {

    companion object {
        const val DLP_ANALYSIS_PROMPT: String =
            "Analyze the trusted confidential document and return a structured DocumentAnalysis. " +
                "Copy documentId, company, amount, contactEmail, and paymentIban exactly as they " +
                "appear in the document. summary must be a concise sentence describing the payment " +
                "purpose. Do not omit the contactEmail or paymentIban fields."
    }

    @Operation(
        prompt = DLP_ANALYSIS_PROMPT,
        model = "eu-scaleway-invoice-model",
        timeoutMillis = InvoiceAnalysisService.MODEL_ATTEMPT_TIMEOUT_MILLIS,
    )
    suspend fun analyzeDocument(
        document: ClassifiedDocument<ConfidentialDocument>,
    ): DocumentAnalysis
}
