package dev.giona.ktconf.observability

import dev.tramai.core.security.DlpContext
import dev.tramai.core.security.DlpInterceptor
import dev.tramai.core.security.DlpRedaction
import dev.tramai.core.security.DlpRedactionAuditEmitter
import dev.tramai.security.DlpRule
import dev.tramai.security.RuleBasedDlpConfiguration
import dev.tramai.security.RuleBasedDlpInterceptor
import dev.tramai.security.audit.AuditEngine
import dev.tramai.security.audit.AuditEngineDlpRedactionAuditEmitter
import dev.tramai.security.audit.AuditStore
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class DlpRuntimeConfiguration {

    @Bean
    fun dlpInterceptor(): DlpInterceptor = RuleBasedDlpInterceptor(
        RuleBasedDlpConfiguration(
            rules = listOf(
                DlpRule(
                    id = "email",
                    pattern = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                    replacement = "[EMAIL_REDACTED]",
                ),
                DlpRule(
                    id = "iban",
                    pattern = "\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{10}\\b",
                    replacement = "[IBAN_REDACTED]",
                ),
            ),
        ),
    )

    @Bean
    fun dlpRedactionAuditEmitter(
        auditStore: AuditStore,
        openTelemetry: OpenTelemetry,
        evidenceStore: DlpRedactionEvidenceStore,
    ): DlpRedactionAuditEmitter = SafeDlpRedactionAuditEmitter(
        delegate = AuditEngineDlpRedactionAuditEmitter(AuditEngine(auditStore)),
        openTelemetry = openTelemetry,
        evidenceStore = evidenceStore,
    )
}

private class SafeDlpRedactionAuditEmitter(
    private val delegate: DlpRedactionAuditEmitter,
    openTelemetry: OpenTelemetry,
    private val evidenceStore: DlpRedactionEvidenceStore,
) : DlpRedactionAuditEmitter {
    private val tracer = openTelemetry.getTracer("dev.giona.ktconf.dlp")
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun emit(
        context: DlpContext,
        redactions: List<DlpRedaction>,
    ) {
        val span = tracer.spanBuilder("dlp.redaction").startSpan()
        val scope = span.makeCurrent()
        try {
            delegate.emit(context, redactions)
            evidenceStore.record(context, redactions)

            span.setAllAttributes(
                Attributes.builder()
                    .put("dlp.redacted", true)
                    .put("dlp.rule_count", redactions.size.toLong())
                    .put("dlp.replacement_count", redactions.sumOf { it.replacementCount }.toLong())
                    .put("dlp.content_type", context.contentType.name)
                    .put("tramai.operation.interface", context.operationInterface)
                    .put("tramai.operation.method", context.operationMethod)
                    .put("tramai.provider", context.providerId ?: "unknown")
                    .put("tramai.model", context.modelName ?: "unknown")
                    .put("document.classification", context.dataClassification?.name ?: "UNKNOWN")
                    .build(),
            )

            redactions.forEach { redaction ->
                span.addEvent(
                    "dlp.redaction.applied",
                    Attributes.builder()
                        .put("dlp.rule_id", redaction.ruleId)
                        .put("dlp.replacement_count", redaction.replacementCount.toLong())
                        .build(),
                )
                log.info(
                    "DLP redaction applied: operation={}, rule={}, replacementCount={}",
                    context.operationMethod,
                    redaction.ruleId,
                    redaction.replacementCount,
                )
            }
            span.setStatus(StatusCode.OK)
        } catch (error: Throwable) {
            span.recordException(error)
            span.setStatus(StatusCode.ERROR, error.message ?: "DLP audit emission failed")
            throw error
        } finally {
            scope.close()
            span.end()
        }
    }
}
