package dev.giona.ktconf.ai

import dev.tramai.core.model.FinishReason
import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.model.ToolCall
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic model provider — the demo's stand-in for a real LLM.
 *
 * Responses are scripted (no network, no nondeterminism). Together with
 * the in-memory payment ledger, this is one of exactly two simulations in
 * the demo — everything downstream of the provider is real TramAI.
 *
 * [responseScript] is consumed in order; the last entry repeats, so repair
 * loops and retries stay deterministic.
 */
class ScriptedProvider(
    private val providerId: String,
    private val responseScript: List<ModelResponse>,
) : ModelProvider {

    private val callCount = AtomicInteger(0)

    override fun providerId(): String = providerId

    override fun supportsCapability(capability: ProviderCapability): Boolean =
        capability == ProviderCapability.TOOL_CALLING ||
            capability == ProviderCapability.STRUCTURED_OUTPUT

    override suspend fun complete(request: ModelRequest): ModelResponse {
        val index = callCount.getAndIncrement()
        return responseScript[minOf(index, responseScript.lastIndex)]
    }

    /** Invocation counter — used to prove policy denials happen BEFORE invocation. */
    fun invocationCount(): Int = callCount.get()
}

/** Scripted responses backing each demo scenario. */
object DemoResponses {

    /** Scenario 1: valid structured assessment for KTCONF-001. */
    val cateringAssessment: ModelResponse = json(
        """
        {
          "invoiceId": "KTCONF-001",
          "supplierName": "KTConf Catering BV",
          "amountCents": 42830,
          "currency": "EUR",
          "risk": "LOW",
          "recommendedAction": "REVIEW_ONLY",
          "rationale": "Conference catering services within budget; no payment required"
        }
        """,
    )

    /** Scenario 3: valid structured assessment for the restricted advisory invoice. */
    val restrictedAdvisoryAssessment: ModelResponse = json(
        """
        {
          "invoiceId": "KTCONF-RESTRICTED-001",
          "supplierName": "ACME Acquisition Advisory",
          "amountCents": 8250000,
          "currency": "EUR",
          "risk": "HIGH",
          "recommendedAction": "REVIEW_ONLY",
          "rationale": "MERGER-2026 advisory services require internal review; no payment scheduled"
        }
        """,
    )

    /** Scenario 2: deliberately broken structured output — the model goes off-script. */
    val brokenAssessment: ModelResponse = json(
        """
        {
          "invoiceId": "KTCONF-INVALID-001",
          "supplierName": "KTConf",
          "amountCents": "banana",
          "currency": "EUR",
          "risk": "YOLO",
          "recommendedAction": "MAYBE_PAY",
          "rationale": "broken deliberately"
        }
        """,
    )

    /** Scenario 4/5: valid structured assessment for the payment invoice. */
    val payAssessment: ModelResponse = json(
        """
        {
          "invoiceId": "KTCONF-PAY-001",
          "supplierName": "KTConf AV & Stage Services BV",
          "amountCents": 1840000,
          "currency": "EUR",
          "risk": "HIGH",
          "recommendedAction": "SCHEDULE_PAYMENT",
          "rationale": "Stage and AV production invoice exceeds threshold; payment required"
        }
        """,
    )

    /** Scenario 4/5: first a schedule-payment tool call, then the typed assessment. */
    val paymentFlow: List<ModelResponse> = listOf(
        ModelResponse(
            content = "I need to schedule the payment for this invoice.",
            toolCalls = listOf(
                ToolCall(
                    id = "call-schedule-payment-ktconf",
                    name = "schedule-payment",
                    argumentsJson =
                        """{"invoiceId":"KTCONF-PAY-001","amountCents":1840000,"currency":"EUR"}""",
                ),
            ),
            finishReason = FinishReason.OTHER,
        ),
        payAssessment,
    )

    private fun json(content: String): ModelResponse =
        ModelResponse(content = content, finishReason = FinishReason.STOP)
}
