package dev.giona.ktconf.application

import dev.giona.ktconf.ai.DocumentAnalysisAi
import dev.giona.ktconf.domain.ConfidentialDocument
import dev.giona.ktconf.domain.DocumentAnalysis
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.giona.ktconf.observability.DlpRedactionEvidenceStore
import dev.giona.ktconf.observability.DlpRedactionSummary
import dev.giona.ktconf.pdf.DlpDemoPdfIngestionService
import dev.giona.ktconf.pdf.DlpTrustedDocument
import dev.giona.ktconf.pdf.TrustedPdfMetadata
import dev.tramai.core.policy.ClassificationSource
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DlpDemoService(
    private val pdfIngestion: DlpDemoPdfIngestionService,
    private val ai: DocumentAnalysisAi,
    private val dlpEvidence: DlpRedactionEvidenceStore,
    openTelemetry: OpenTelemetry,
) {
    private val tracer = openTelemetry.getTracer("dev.giona.ktconf.dlp")
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(file: MultipartFile): DlpDemoResult {
        val span = tracer.spanBuilder("document.dlp.analyze").startSpan()
        val scope = span.makeCurrent()
        return try {
            val trusted = traceIngest(file)
            span.setAttribute("document.id", trusted.document.documentId)
            span.setAttribute("document.classification", trusted.metadata.classification.name)
            span.setAttribute("document.residency", trusted.metadata.residency.name)
            span.setAttribute("tramai.route", InvoiceRoute.EU_CLOUD.name)
            span.setAttribute("tramai.operation", "analyzeDocument")
            log.info(
                "DLP demo document accepted: documentId={}, classification={}",
                trusted.document.documentId,
                trusted.metadata.classification,
            )

            val checkpoint = dlpEvidence.checkpoint()
            val analysis = ai.analyzeDocument(
                trusted.document.toClassifiedDocument(ClassificationSource.RULE_BASED),
            )
            log.info("AI operation completed: documentId={}", trusted.document.documentId)

            val summary = dlpEvidence.summarySince(checkpoint, "analyzeDocument")
            validateBoundary(analysis, summary)
            span.setAttribute("dlp.redacted", true)
            span.setAttribute("dlp.replacement_count", summary.replacementCount.toLong())
            span.setStatus(StatusCode.OK)

            DlpDemoResult(
                document = trusted.document.safeView(),
                metadata = trusted.metadata,
                selectedRoute = InvoiceRoute.EU_CLOUD,
                classificationSource = ClassificationSource.RULE_BASED,
                operation = "analyzeDocument",
                analysis = analysis,
                dlp = summary.toView(),
            )
        } catch (error: Throwable) {
            span.recordException(error)
            span.setStatus(StatusCode.ERROR, error.message ?: "DLP demo failed")
            throw error
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun traceIngest(file: MultipartFile): DlpTrustedDocument {
        val span = tracer.spanBuilder("document.ingest").startSpan()
        val scope = span.makeCurrent()
        return try {
            pdfIngestion.readTrustedDocument(file).also { trusted ->
                span.setAttribute("document.id", trusted.document.documentId)
                span.setAttribute("document.classification", trusted.metadata.classification.name)
                span.setAttribute("document.residency", trusted.metadata.residency.name)
                span.setAttribute("document.contains_email", true)
                span.setAttribute("document.contains_iban", true)
                span.setStatus(StatusCode.OK)
            }
        } catch (error: Throwable) {
            span.recordException(error)
            span.setStatus(StatusCode.ERROR, error.message ?: "Document ingest failed")
            throw error
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun validateBoundary(
        analysis: DocumentAnalysis,
        summary: DlpRedactionSummary,
    ) {
        require(analysis.contactEmail == "[EMAIL_REDACTED]") {
            "DLP demo expected contactEmail to be redacted by TramAI"
        }
        require(analysis.paymentIban == "[IBAN_REDACTED]") {
            "DLP demo expected paymentIban to be redacted by TramAI"
        }
        val rules = summary.appliedRules.associate { it.ruleId to it.replacementCount }
        require(rules == mapOf("email" to 1, "iban" to 1)) {
            "DLP demo expected one email and one iban redaction"
        }
        require(summary.replacementCount == 2) {
            "DLP demo expected exactly two redactions"
        }
    }
}

data class DlpDemoResult(
    val document: DlpDocumentView,
    val metadata: TrustedPdfMetadata,
    val selectedRoute: InvoiceRoute,
    val classificationSource: ClassificationSource,
    val operation: String,
    val analysis: DocumentAnalysis,
    val dlp: DlpView,
)

data class DlpDocumentView(
    val documentId: String,
    val company: String,
    val amount: String,
    val purpose: String,
    val sensitiveSignals: List<String>,
)

data class DlpView(
    val redacted: Boolean,
    val replacementCount: Int,
    val ruleIds: List<String>,
    val audit: List<DlpAuditView>,
)

data class DlpAuditView(
    val decision: String,
    val ruleId: String,
    val replacementCount: Int,
)

private fun ConfidentialDocument.safeView(): DlpDocumentView = DlpDocumentView(
    documentId = documentId,
    company = company,
    amount = amount,
    purpose = purpose,
    sensitiveSignals = listOf("EMAIL DETECTED", "IBAN DETECTED"),
)

private fun DlpRedactionSummary.toView(): DlpView = DlpView(
    redacted = true,
    replacementCount = replacementCount,
    ruleIds = appliedRules.map { it.ruleId },
    audit = appliedRules.map { rule ->
        DlpAuditView(
            decision = "REDACTED",
            ruleId = rule.ruleId,
            replacementCount = rule.replacementCount,
        )
    },
)
