package dev.giona.ktconf.observability

import dev.giona.ktconf.application.InvoiceRoute
import dev.tramai.core.exception.PolicyViolationException
import dev.tramai.core.policy.DataClassification
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Application-owned telemetry for decisions made before a provider attempt
 * exists. TramAI's operation observer owns the nested model-attempt spans.
 *
 * Deliberately excludes invoice content and identifiers.
 */
@Component
class GovernanceTelemetry(openTelemetry: OpenTelemetry) {
    private val tracer = openTelemetry.getTracer("dev.giona.ktconf.governance")
    private val routeCounter = openTelemetry.getMeter("dev.giona.ktconf.governance")
        .counterBuilder("ktconf.governance.routes")
        .setDescription("Application-selected AI routes")
        .build()
    private val policyDenials = openTelemetry.getMeter("dev.giona.ktconf.governance")
        .counterBuilder("ktconf.governance.policy_denials")
        .setDescription("TramAI policy denials observed by the application")
        .build()
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun <T> traceModelCall(
        classification: DataClassification,
        route: InvoiceRoute,
        action: suspend () -> T,
    ): T {
        val target = route.target()
        val attributes = Attributes.builder()
            .put("invoice.classification", classification.name)
            .put("invoice.route", route.name)
            .put("tramai.logical_model", target.logicalModel)
            .put("tramai.provider", target.providerId)
            .put("tramai.trust_zone", target.trustZone)
            .build()
        val span = tracer.spanBuilder("invoice.model.call").startSpan()
        span.setAllAttributes(attributes)
        routeCounter.add(1, attributes)
        log.info(
            "AI route selected: classification={}, route={}, logicalModel={}, provider={}, trustZone={}",
            classification,
            route,
            target.logicalModel,
            target.providerId,
            target.trustZone,
        )

        val scope = span.makeCurrent()
        return try {
            action()
        } catch (error: PolicyViolationException) {
            recordPolicyDenial(span, attributes, error)
            throw error
        } catch (error: Throwable) {
            span.recordException(error)
            span.setStatus(StatusCode.ERROR, error.message ?: "AI operation failed")
            throw error
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun recordPolicyDenial(
        span: io.opentelemetry.api.trace.Span,
        attributes: Attributes,
        error: PolicyViolationException,
    ) {
        val decision = error.decision
        val decisionAttributes = Attributes.builder()
            .putAll(attributes)
            .put("governance.decision", "deny")
            .put("governance.reason_code", decision.reasonCode)
            .build()
        span.addEvent("governance.policy.denied", decisionAttributes)
        span.recordException(error)
        span.setStatus(StatusCode.ERROR, decision.reasonCode)
        policyDenials.add(1, decisionAttributes)
        log.warn("TramAI policy denied model call: reasonCode={}", decision.reasonCode)
    }
}

private data class ModelTarget(
    val logicalModel: String,
    val providerId: String,
    val trustZone: String,
)

private fun InvoiceRoute.target(): ModelTarget = when (this) {
    InvoiceRoute.CLOUD -> ModelTarget("cloud-invoice-model", "cloud-provider", "GLOBAL_CLOUD")
    InvoiceRoute.LOCAL -> ModelTarget("local-invoice-model", "local-provider", "LOCAL")
}
