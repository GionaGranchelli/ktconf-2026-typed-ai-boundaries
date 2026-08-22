package dev.giona.ktconf.application

import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.model.ClassifiedDocument

/**
 * Application port for invoice analysis. Configuration provides either the
 * governed [dev.giona.ktconf.ai.InvoiceAnalysisService] adapter (tools,
 * approval, deterministic scripts) or the narrower
 * [dev.giona.ktconf.ai.RealInvoiceAnalysisService] adapter (real LLM, no
 * tools). [InvoiceApplicationService] never knows which.
 */
fun interface InvoiceAnalyzer {
    suspend fun analyze(document: ClassifiedDocument<InvoiceDocument>): InvoiceAssessment
}
