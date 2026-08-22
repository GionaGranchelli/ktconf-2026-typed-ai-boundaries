package dev.giona.ktconf.ai

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.annotations.AiService
import dev.tramai.core.annotations.Operation
import dev.tramai.core.annotations.User
import dev.tramai.core.model.ClassifiedDocument

/**
 * AI service contract — the ONLY AI boundary the application sees.
 *
 * Business code calls [analyze] with a typed classified document and gets
 * a typed assessment back. Prompt construction, schema generation, model
 * routing, policy enforcement and approval suspension all happen inside
 * TramAI, behind this interface.
 */
@AiService
fun interface InvoiceAnalysisService {

    @Operation(model = "invoice-model", tools = ["schedule-payment"])
    @User(
        """
        Analyze this classified invoice document.
        Return a typed assessment.
        Schedule payment only when the invoice requires execution.

        Invoice:
        {document}
        """,
    )
    suspend fun analyze(document: ClassifiedDocument<InvoiceDocument>): InvoiceAssessment
}
