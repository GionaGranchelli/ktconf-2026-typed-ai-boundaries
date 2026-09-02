package dev.giona.ktconf.application

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.domain.toClassifiedDocument
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.giona.ktconf.notifications.FakeEmailService
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.tramai.core.exception.ApprovalSuspendedException
import dev.tramai.core.policy.DataClassification
import dev.tramai.core.policy.ClassificationSource
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Ordinary application service. Three separate concerns stay visible:
 *
 * 1. CLASSIFICATION — supplied by the request/upstream (never inferred by
 *    the model). "Classification is supplied."
 * 2. ROUTING — this exhaustive `when` selects the normal model route.
 *    "Routing chooses."
 * 3. POLICY ENFORCEMENT — TramAI validates whether the selected route is
 *    allowed for that classification. "Policy enforces."
 *
 * The route is an APPLICATION-owned choice ([InvoiceRoute]); the YAML owns
 * model → provider → trust zone. TramAI stays the independent backstop: if
 * the application routes CONFIDENTIAL/RESTRICTED data to cloud anyway,
 * TramAI denies it before provider invocation.
 *
 * A HIGH-risk tool request suspends the workflow: TramAI raises
 * [ApprovalSuspendedException], the approval is registered server-side, and
 * the HTTP request finishes with 202. The workflow did not.
 */
@Service
class InvoiceService(
    private val ai: InvoiceAnalysisService,
    private val registry: PendingApprovalRegistry,
    private val telemetry: GovernanceTelemetry,
    private val email: FakeEmailService,
    private val ledger: InMemoryPaymentLedger,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(request: AnalyzeInvoiceRequest, routeOverride: InvoiceRoute? = null, classificationSource: ClassificationSource = ClassificationSource.DECLARED): AnalyzeOutcome {
        val document = request.toClassifiedDocument(classificationSource)
        val route = routeOverride ?: when (document.classification) {
                DataClassification.PUBLIC,
                DataClassification.INTERNAL, -> InvoiceRoute.CLOUD

                DataClassification.CONFIDENTIAL,
                DataClassification.RESTRICTED, -> InvoiceRoute.LOCAL
            }
        return telemetry.traceModelCall(document.classification, route) {
            try {
                val rawAssessment = when (route) {
                    InvoiceRoute.CLOUD -> if (requiresPaymentApproval(document.payload)) {
                        ai.analyzeCloudPayment(document)
                    } else {
                        ai.analyzeCloudAutoPayment(document)
                    }
                    InvoiceRoute.LOCAL -> if (requiresPaymentApproval(document.payload)) {
                        ai.analyzeLocal(document)
                    } else {
                        ai.analyzeLocalAutoPayment(document)
                    }
                    InvoiceRoute.LOCAL_NVIDIA -> if (requiresPaymentApproval(document.payload)) {
                        ai.analyzeLocalNvidiaPayment(document)
                    } else {
                        ai.analyzeLocalNvidiaAutoPayment(document)
                    }
                    InvoiceRoute.EU_CLOUD -> if (requiresPaymentApproval(document.payload)) {
                        ai.analyzeEuScalewayPayment(document)
                    } else {
                        ai.analyzeEuScalewayAutoPayment(document)
                    }
                    InvoiceRoute.GLOBAL_CLOUD -> if (requiresPaymentApproval(document.payload)) {
                        ai.analyzeGlobalNvidiaPayment(document)
                    } else {
                        ai.analyzeGlobalNvidiaAutoPayment(document)
                    }
                }
                val paymentScheduled = !requiresPaymentApproval(document.payload) &&
                    ledger.hasExecutionForInvoice(document.payload.invoiceId)
                AnalyzeOutcome.Typed(
                    assessment = reconcileAssessment(document.payload, rawAssessment, paymentScheduled),
                    selectedRoute = route,
                    classificationSource = classificationSource,
                    paymentScheduled = paymentScheduled,
                )
            } catch (e: ApprovalSuspendedException) {
                log.info("Workflow suspended by approval gate: approvalId={}, workflowRunId={}, tool={}", e.approvalId, e.workflowRunId, e.toolName)
                val pending = registry.register(e)
                val notification = telemetry.traceApprovalNotification(
                    route = route,
                    toolName = pending.toolName,
                    recipient = FakeEmailService.APPROVER_ADDRESS,
                ) {
                    email.sendApprovalRequest(
                        to = FakeEmailService.APPROVER_ADDRESS,
                        invoiceId = document.payload.invoiceId,
                        approvalId = pending.approvalId,
                    )
                }
                AnalyzeOutcome.AwaitingApproval(
                    selectedRoute = route,
                    approvalId = pending.approvalId,
                    workflowRunId = pending.workflowRunId,
                    toolName = pending.toolName,
                    rationale = "Payment scheduling requires human approval because invoice ${document.payload.invoiceId} is a high-risk write action.",
                    classificationSource = classificationSource,
                    approvalExpiresAt = pending.expiresAt,
                    notificationStatus = "RECORDED",
                    notificationRecipient = notification.to,
                    notificationSubject = notification.subject,
                )
            }
        }
    }

    /**
     * DEMO-ONLY boundary proof: intentionally send a RESTRICTED document
     * through the cloud operation, using the SAME runtime and SAME operation
     * as normal routing. TramAI must deny it before provider invocation
     * (HTTP 403, cloud invocation delta = 0). This is fault injection,
     * NOT production routing logic.
     */
    suspend fun analyzeRestrictedViaCloud(request: AnalyzeInvoiceRequest): InvoiceAssessment {
        val document = request.toClassifiedDocument()
        return telemetry.traceModelCall(document.classification, InvoiceRoute.CLOUD) {
            ai.analyzeCloud(document)
        }.also {
            log.error("Boundary proof unexpectedly completed: invoiceId={} reached cloud operation", request.invoice.invoiceId)
        }
    }

    /** DEMO-ONLY boundary proof: force a RESTRICTED document to EU_CLOUD. */
    suspend fun analyzeRestrictedViaEu(request: AnalyzeInvoiceRequest): InvoiceAssessment {
        val document = request.toClassifiedDocument()
        return telemetry.traceModelCall(document.classification, InvoiceRoute.EU_CLOUD) {
            ai.analyzeEuScaleway(document)
        }.also {
            log.error("Boundary proof unexpectedly completed: invoiceId={} reached EU provider", request.invoice.invoiceId)
        }
    }

    /** Explicit task-002 smoke path; normal application routing is unchanged. */
    suspend fun analyzeGlobalNvidia(request: AnalyzeInvoiceRequest): InvoiceAssessment {
        val document = request.toClassifiedDocument()
        return telemetry.traceModelCall(document.classification, InvoiceRoute.GLOBAL_CLOUD) {
            ai.analyzeGlobalNvidia(document)
        }
    }

    /** Explicit task-003 smoke path; normal route selection is unchanged. */
    suspend fun analyzeLocalNvidia(request: AnalyzeInvoiceRequest): InvoiceAssessment {
        val document = request.toClassifiedDocument()
        return telemetry.traceModelCall(document.classification, InvoiceRoute.LOCAL_NVIDIA) {
            ai.analyzeLocalNvidia(document)
        }
    }

    /** Explicit task-004 smoke path; normal route selection is unchanged. */
    suspend fun analyzeEuScaleway(request: AnalyzeInvoiceRequest): InvoiceAssessment {
        val document = request.toClassifiedDocument()
        return telemetry.traceModelCall(document.classification, InvoiceRoute.EU_CLOUD) {
            ai.analyzeEuScaleway(document)
        }
    }

    /**
     * The model may return a well-typed but semantically inconsistent result.
     * Trusted invoice fields and the deterministic approval threshold remain
     * authoritative; this prevents a model from labeling €1,200 as HIGH or
     * changing the amount shown to the operator.
     */
    private fun reconcileAssessment(
        invoice: InvoiceDocument,
        model: InvoiceAssessment,
        paymentScheduled: Boolean,
    ): InvoiceAssessment {
        val highRisk = invoice.amountCents > APPROVAL_THRESHOLD_CENTS
        val expectedRisk = if (highRisk) InvoiceRisk.HIGH else InvoiceRisk.LOW
        val expectedAction = when {
            !highRisk && paymentScheduled -> InvoiceAction.SCHEDULE_PAYMENT
            !highRisk -> InvoiceAction.REVIEW_ONLY
            else -> InvoiceAction.REQUEST_HUMAN_APPROVAL
        }
        val inconsistent = model.invoiceId != invoice.invoiceId ||
            model.supplierName != invoice.supplierName ||
            model.amountCents != invoice.amountCents ||
            model.currency != invoice.currency ||
            model.risk != expectedRisk ||
            model.recommendedAction != expectedAction
        return model.copy(
            invoiceId = invoice.invoiceId,
            supplierName = invoice.supplierName,
            amountCents = invoice.amountCents,
            currency = invoice.currency,
            risk = expectedRisk,
            recommendedAction = expectedAction,
            rationale = if (inconsistent) trustedRationale(invoice, highRisk, paymentScheduled) else model.rationale,
        )
    }

    private fun trustedRationale(invoice: InvoiceDocument, highRisk: Boolean, paymentScheduled: Boolean = false): String {
        val euros = "%.2f".format(java.util.Locale.ROOT, invoice.amountCents / 100.0)
        return if (highRisk) {
            "Trusted invoice amount is €$euros and exceeds the €5,000 approval threshold; human approval is required."
        } else if (paymentScheduled) {
            "Trusted invoice amount is €$euros and is within the €5,000 automatic-payment threshold; payment was executed exactly once."
        } else {
            "Trusted invoice amount is €$euros and is within the €5,000 automatic-payment threshold; automatic payment was not executed."
        }
    }

    private fun requiresPaymentApproval(invoice: InvoiceDocument): Boolean =
        invoice.amountCents > APPROVAL_THRESHOLD_CENTS

    private companion object {
        const val APPROVAL_THRESHOLD_CENTS = 500_000L
    }
}

/**
 * Application-owned route choice. The YAML (not this enum) decides which
 * model/provider/trust zone backs each route.
 */
enum class InvoiceRoute { LOCAL, LOCAL_NVIDIA, EU_CLOUD, CLOUD, GLOBAL_CLOUD }

sealed interface AnalyzeOutcome {
    data class Typed(
        val assessment: InvoiceAssessment,
        val selectedRoute: InvoiceRoute,
        val classificationSource: ClassificationSource,
        val paymentScheduled: Boolean = false,
    ) : AnalyzeOutcome

    data class AwaitingApproval(
        val selectedRoute: InvoiceRoute,
        val approvalId: String,
        val workflowRunId: String,
        val toolName: String,
        val rationale: String,
        val classificationSource: ClassificationSource,
        val notificationStatus: String,
        val notificationRecipient: String,
        val notificationSubject: String,
        val approvalExpiresAt: Instant,
    ) : AnalyzeOutcome
}
