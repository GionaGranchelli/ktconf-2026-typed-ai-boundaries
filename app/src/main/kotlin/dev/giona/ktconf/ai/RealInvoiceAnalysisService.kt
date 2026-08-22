package dev.giona.ktconf.ai

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.annotations.AiService
import dev.tramai.core.annotations.Operation
import dev.tramai.core.annotations.User
import dev.tramai.core.model.ClassifiedDocument

/**
 * AI service contract for the real-model path (`typed --real`).
 *
 * A NARROWER real-model service preserving the same typed input/output
 * contract as [InvoiceAnalysisService] (ClassifiedDocument<InvoiceDocument>
 * → InvoiceAssessment), deliberately exposing NO tools: the live-model
 * smoke test receives less authority than the deterministic governance
 * service. Tool governance stays deterministic (see InvoiceAnalysisService).
 */
@AiService
fun interface RealInvoiceAnalysisService {

    @Operation(model = "invoice-model")
    @User(
        """
        Analyze this classified invoice document.
        Return a typed assessment.

        Invoice:
        {document}
        """,
    )
    suspend fun analyze(document: ClassifiedDocument<InvoiceDocument>): InvoiceAssessment
}
