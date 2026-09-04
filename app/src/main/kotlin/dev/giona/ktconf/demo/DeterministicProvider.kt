package dev.giona.ktconf.demo

import dev.tramai.core.model.MessageRole
import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory

/**
 * Input-driven deterministic model provider — the demo's stand-in for a
 * real LLM.
 *
 * The response depends ONLY on the invoice id in the user message and on
 * whether the engine already appended a tool result. There is NO global
 * first-call/second-call sequence, so concurrent requests cannot consume
 * each other's scripted responses.
 *
 * Unknown input is a HARD failure — a deterministic provider must never
 * silently fall back (that would make it magical test machinery).
 *
 * [invocationCount] is demo/test instrumentation: the cloud provider must
 * never be invoked for RESTRICTED data, so its counter proves policy
 * denials happen BEFORE invocation.
 */
class DeterministicProvider(
    private val providerId: String,
    private val script: (invoiceId: String, toolResultPresent: Boolean, request: ModelRequest) -> ModelResponse,
) : ModelProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    private val callCount = AtomicInteger(0)

    override fun providerId(): String = providerId

    override fun supportsCapability(capability: ProviderCapability): Boolean =
        capability == ProviderCapability.TOOL_CALLING ||
            capability == ProviderCapability.STRUCTURED_OUTPUT

    override suspend fun complete(request: ModelRequest): ModelResponse {
        val userContent = request.messages
            .filter { it.role == MessageRole.USER }
            .joinToString("\n") { it.content }
        val invoiceId = KNOWN_INVOICE_IDS.firstOrNull { userContent.contains(it) }
            ?: error("DeterministicProvider($providerId): no deterministic response for request (no known invoice id)")
        val toolResultPresent = request.messages.any { it.role == MessageRole.TOOL }
        val call = callCount.incrementAndGet()
        log.info("Deterministic provider response selected: providerId={}, invoiceId={}, toolResultPresent={}, invocation={}", providerId, invoiceId, toolResultPresent, call)
        return script(invoiceId, toolResultPresent, request)
    }

    /** Invocation counter — used to prove policy denials happen BEFORE invocation. */
    fun invocationCount(): Int = callCount.get()

    companion object {
        /** Longest ids first so prefix-ish matches resolve deterministically. */
        val KNOWN_INVOICE_IDS = listOf(
            "KTCONF-PAY-001",
            "KTCONF-PAY-002",
            "KTCONF-PUBLIC-HIGH",
            "KTCONF-LOCAL-HIGH",
            "KTCONF-EU-LOW",
            "KTCONF-RESTRICTED-001",
            "KTCONF-RESTRICTED",
            "KTCONF-INVALID-001",
            "KTCONF-PUBLIC",
            "KTCONF-EU",
            "KTCONF-001",
        )
    }
}

/** Local route script: the full payment story plus valid/invalid fixtures. */
fun localScript(invoiceId: String, toolResultPresent: Boolean, request: ModelRequest): ModelResponse = when {
    invoiceId == "KTCONF-INVALID-001" -> DemoResponses.invalidOutput
    isAutoPaymentOperation(request) && !toolResultPresent ->
        DemoResponses.autoPaymentToolCall(invoiceId, requestAmount(request, invoiceId))
    isAutoPaymentOperation(request) ->
        DemoResponses.autoPayAssessment(invoiceId, requestAmount(request, invoiceId))
    request.model == "local-nvidia-invoice-model" && isHighRiskPaymentInvoice(invoiceId) && !toolResultPresent ->
        DemoResponses.paymentToolCall(invoiceId, requestAmount(request, invoiceId))
    request.model == "local-nvidia-invoice-model" && isHighRiskPaymentInvoice(invoiceId) ->
        DemoResponses.payAssessment(invoiceId, requestAmount(request, invoiceId))
    request.model == "local-assessment-model" && invoiceId.startsWith("KTCONF-PAY-") ->
        DemoResponses.paymentPreAssessment(paymentAmount(invoiceId).first, paymentAmount(invoiceId).second)
    request.model == "local-assessment-model" && invoiceId == "KTCONF-001" ->
        DemoResponses.cateringAssessment
    request.model == "local-assessment-model" && invoiceId == "KTCONF-RESTRICTED-001" ->
        DemoResponses.restrictedLocalAssessment(invoiceId)
    request.model == "local-assessment-model" && invoiceId == "KTCONF-INVALID-001" ->
        DemoResponses.invalidOutput
    invoiceId.startsWith("KTCONF-PAY-") && !toolResultPresent ->
        DemoResponses.paymentToolCall(paymentAmount(invoiceId).first, paymentAmount(invoiceId).second)

    invoiceId.startsWith("KTCONF-PAY-") ->
        DemoResponses.payAssessment(paymentAmount(invoiceId).first, paymentAmount(invoiceId).second)

    invoiceId == "KTCONF-RESTRICTED-001" || invoiceId == "KTCONF-RESTRICTED" -> DemoResponses.restrictedLocalAssessment(invoiceId)
    invoiceId == "KTCONF-001" || invoiceId == "KTCONF-PUBLIC" || invoiceId == "KTCONF-EU" -> DemoResponses.cateringAssessment
    else -> error("local provider: no deterministic response for invoice $invoiceId")
}

private val PAYMENT_AMOUNTS = mapOf(
    "KTCONF-PAY-001" to 1_840_000L,
    "KTCONF-PAY-002" to 950_000L,
)

private fun paymentAmount(invoiceId: String): Pair<String, Long> =
    PAYMENT_AMOUNTS[invoiceId]?.let { invoiceId to it }
        ?: error("local provider: no payment amount for invoice $invoiceId")

private fun deterministicInvoiceAmount(invoiceId: String): Long = when (invoiceId) {
    "KTCONF-001" -> 42_830L
    "KTCONF-RESTRICTED", "KTCONF-RESTRICTED-001" -> 4_200L
    "KTCONF-PUBLIC" -> 120_000L
    "KTCONF-EU-LOW" -> 120_000L
    "KTCONF-PAY-001", "KTCONF-PUBLIC-HIGH", "KTCONF-LOCAL-HIGH", "KTCONF-EU" -> 1_840_000L
    "KTCONF-PAY-002" -> 950_000L
    else -> error("deterministic provider requires a known invoice amount: $invoiceId")
}

private fun requestAmount(request: ModelRequest, invoiceId: String): Long {
    val content = request.messages.joinToString("\n") { it.content }
    return Regex("\\\"amountCents\\\"\\s*:\\s*(\\d+)").find(content)?.groupValues?.get(1)?.toLong()
        ?: deterministicInvoiceAmount(invoiceId)
}

private fun isHighRiskPaymentInvoice(invoiceId: String): Boolean =
    invoiceId.startsWith("KTCONF-PAY-") || invoiceId == "KTCONF-LOCAL-HIGH"

private fun isAutoPaymentOperation(request: ModelRequest): Boolean =
    request.operationMethod?.contains("AutoPayment") == true

/**
 * Cloud route script. RESTRICTED fixtures are deliberately ABSENT: if a
 * RESTRICTED request ever reaches the cloud provider, that is a policy
 * breach and must fail loudly (the oracle asserts the counter stays 0).
 */
fun cloudScript(invoiceId: String, toolResultPresent: Boolean, request: ModelRequest): ModelResponse = when {
    invoiceId == "KTCONF-INVALID-001" -> DemoResponses.invalidOutput
    isAutoPaymentOperation(request) && !toolResultPresent ->
        DemoResponses.autoPaymentToolCall(invoiceId, requestAmount(request, invoiceId))
    isAutoPaymentOperation(request) ->
        DemoResponses.autoPayAssessment(invoiceId, requestAmount(request, invoiceId))
    request.model in setOf("cloud-invoice-model", "global-nvidia-invoice-model") && invoiceId in setOf("KTCONF-PUBLIC-HIGH", "KTCONF-EU") && !toolResultPresent ->
        DemoResponses.paymentToolCall(invoiceId, requestAmount(request, invoiceId))
    request.model in setOf("cloud-invoice-model", "global-nvidia-invoice-model") && invoiceId in setOf("KTCONF-PUBLIC-HIGH", "KTCONF-EU") ->
        DemoResponses.payAssessment(invoiceId, requestAmount(request, invoiceId))
    request.model in setOf("cloud-invoice-model", "global-nvidia-invoice-model") && invoiceId == "KTCONF-PAY-001" && !toolResultPresent ->
        DemoResponses.paymentToolCall(invoiceId, 1_840_000L)
    request.model in setOf("cloud-invoice-model", "global-nvidia-invoice-model") && invoiceId == "KTCONF-PAY-001" ->
        DemoResponses.payAssessment(invoiceId, 1_840_000L)
    request.model == "eu-scaleway-invoice-model" && invoiceId == "KTCONF-EU" && !toolResultPresent ->
        DemoResponses.paymentToolCall(invoiceId, 1_840_000L)
    request.model == "eu-scaleway-invoice-model" && invoiceId == "KTCONF-EU" ->
        DemoResponses.payAssessment(invoiceId, 1_840_000L)
    invoiceId == "KTCONF-001" || invoiceId == "KTCONF-PUBLIC" || invoiceId == "KTCONF-EU" -> DemoResponses.cateringAssessment
    else -> error("cloud provider: no deterministic response for invoice $invoiceId (RESTRICTED data must never reach the cloud)")
}
