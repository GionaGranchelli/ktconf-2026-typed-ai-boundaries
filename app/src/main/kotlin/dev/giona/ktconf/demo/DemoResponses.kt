package dev.giona.ktconf.demo

import dev.tramai.core.model.FinishReason
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.model.ToolCall

/** Scripted responses backing the deterministic provider. */
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

    /** Broken profile: deliberately invalid structured output — the model goes off-script. */
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

    /** KTCONF-PAY-001, turn 1: request the HIGH-risk schedule-payment tool. */
    val paymentToolCall: ModelResponse = ModelResponse(
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
    )

    /** KTCONF-PAY-001, turn 2 (after the tool result): the typed assessment. */
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

    private fun json(content: String): ModelResponse =
        ModelResponse(content = content, finishReason = FinishReason.STOP)
}
