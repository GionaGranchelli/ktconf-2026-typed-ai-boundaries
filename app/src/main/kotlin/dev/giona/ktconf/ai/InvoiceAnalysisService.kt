package dev.giona.ktconf.ai

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.annotations.AiService
import dev.tramai.core.annotations.Operation
import dev.tramai.core.annotations.User
import dev.tramai.core.model.ClassifiedDocument

/**
 * Primary governed AI service boundary used by the deterministic scenarios.
 *
 * Business code calls [analyze] with a typed classified document and gets
 * a typed assessment back. Prompt construction, schema generation, model
 * routing, policy enforcement and approval suspension all happen inside
 * TramAI, behind this interface. The real-model path uses the narrower
 * [RealInvoiceAnalysisService] with the same typed input/output contract
 * but no tools.
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
