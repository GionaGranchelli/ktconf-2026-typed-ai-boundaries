package dev.giona.ktconf.demo

import dev.tramai.core.model.FinishReason
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.model.ToolCall

/** Input-driven responses backing the deterministic providers. */
object DemoResponses {

    /** KTCONF-001: valid structured assessment for the catering invoice. */
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

    /** KTCONF-RESTRICTED-001: valid assessment for the advisory invoice. */
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

    /** KTCONF-INVALID-001: deliberately invalid structured output — the model goes off-script. */
    val invalidOutput: ModelResponse = json(
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

    /** KTCONF-PAY-00x, turn 1: request the HIGH-risk schedule-payment tool. */
    fun paymentToolCall(invoiceId: String, amountCents: Long): ModelResponse = ModelResponse(
        content = "I need to schedule the payment for this invoice.",
        toolCalls = listOf(
            ToolCall(
                id = "call-schedule-payment-ktconf",
                name = "schedule-payment",
                argumentsJson =
                    """{"invoiceId":"$invoiceId","amountCents":$amountCents,"currency":"EUR"}""",
            ),
        ),
        finishReason = FinishReason.OTHER,
    )

    /** KTCONF-PAY-00x, turn 2 (after the tool result): the typed assessment. */
    fun payAssessment(invoiceId: String, amountCents: Long): ModelResponse = json(
        """
        {
          "invoiceId": "$invoiceId",
          "supplierName": "KTConf AV & Stage Services BV",
          "amountCents": $amountCents,
          "currency": "EUR",
          "risk": "HIGH",
          "recommendedAction": "SCHEDULE_PAYMENT",
          "rationale": "Stage and AV production invoice exceeds threshold; payment required"
        }
        """,
    )

    private fun json(content: String): ModelResponse =
        ModelResponse(content = content, finishReason = FinishReason.STOP)
}
