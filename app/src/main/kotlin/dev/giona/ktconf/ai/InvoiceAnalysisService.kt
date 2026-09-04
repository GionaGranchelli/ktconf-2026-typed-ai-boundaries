package dev.giona.ktconf.ai

import dev.tramai.core.annotations.AiService
import dev.tramai.core.annotations.Operation
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.tramai.core.model.ClassifiedDocument

/**
 * The typed AI boundary — the ONE contract the audience should remember.
 *
 * Governed operations on one service:
 * - [analyzeLocal] runs on `local-invoice-model` (LOCAL provider) and may
 *   request the HIGH-risk `schedule-payment` tool.
 * - [preAssessLocal] produces a tool-free recommendation for the explicit
 *   workflow demo, before any side effect is attempted.
 * - [analyzeCloud] runs on `cloud-invoice-model` (GLOBAL_CLOUD provider)
 *   and has no tools.
 *
 * The application chooses which operation to call. TramAI validates whether
 * that route is allowed for the document's classification.
 */
@AiService
interface InvoiceAnalysisService {

    companion object {
        /** Per provider attempt; the HTTP workflow deadline is configured separately. */
        const val MODEL_ATTEMPT_TIMEOUT_MILLIS: Long = 90_000

        /** TramAI derives the approval challenge expiry from the operation timeout. */
        const val PAYMENT_APPROVAL_TIMEOUT_MILLIS: Long = 10 * 60 * 1_000

        const val ASSESSMENT_PROMPT: String =
            "Analyze the invoice document and return a structured InvoiceAssessment. " +
                "Any value above 5,000 EUR is HIGH risk and uses " +
                "recommendedAction=REQUEST_HUMAN_APPROVAL. Any value at or below 5,000 EUR " +
                "is LOW risk and uses recommendedAction=REVIEW_ONLY, unless a successful " +
                "workflow tool result changes the action. Return confidence " +
                "as a number from 0.0 to 1.0 inclusive. The recommendedAction field " +
                "must be a JSON string enum, exactly one of REVIEW_ONLY, " +
                "REQUEST_HUMAN_APPROVAL, or SCHEDULE_PAYMENT; do not return an object " +
                "for this field."

        const val TOOL_PROMPT: String =
            "$ASSESSMENT_PROMPT When human approval is required, request the " +
                "schedule-payment tool; TramAI will enforce approval before execution. " +
                "Before a successful schedule-payment tool result, an amount above " +
                "5,000 EUR must use REQUEST_HUMAN_APPROVAL and request that tool. " +
                "For an amount at or below 5,000 EUR, do not request schedule-payment " +
                "and return REVIEW_ONLY. " +
                "After a successful schedule-payment tool result, do not request the " +
                "tool again and return SCHEDULE_PAYMENT."

        const val AUTO_PAYMENT_PROMPT: String =
            "$ASSESSMENT_PROMPT For a trusted invoice amount at or below 5,000 EUR, " +
                "the application has selected the low-risk automatic-payment workflow. " +
                "You MUST call auto-schedule-payment exactly once with the trusted invoice " +
                "fields before returning the final assessment. After the successful tool " +
                "result, return SCHEDULE_PAYMENT and do not call the tool again."
    }

    @Operation(
        prompt = TOOL_PROMPT,
        model = "local-invoice-model",
        tools = ["schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeLocal(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = AUTO_PAYMENT_PROMPT,
        model = "local-invoice-model",
        tools = ["auto-schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeLocalAutoPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** Explicit contest typed-inference operation for the configured local NVIDIA model. */
    @Operation(
        prompt = ASSESSMENT_PROMPT,
        model = "local-nvidia-invoice-model",
        timeoutMillis = MODEL_ATTEMPT_TIMEOUT_MILLIS,
    )
    suspend fun analyzeLocalNvidia(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** Contest payment proof: the real NVIDIA model may request the governed tool. */
    @Operation(
        prompt = TOOL_PROMPT,
        model = "local-nvidia-invoice-model",
        tools = ["schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeLocalNvidiaPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = AUTO_PAYMENT_PROMPT,
        model = "local-nvidia-invoice-model",
        tools = ["auto-schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeLocalNvidiaAutoPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** Tool-free assessment used by the two-phase approval demonstration. */
    @Operation(
        prompt = ASSESSMENT_PROMPT,
        model = "local-assessment-model",
        timeoutMillis = MODEL_ATTEMPT_TIMEOUT_MILLIS,
    )
    suspend fun preAssessLocal(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = ASSESSMENT_PROMPT,
        model = "cloud-invoice-model",
        timeoutMillis = MODEL_ATTEMPT_TIMEOUT_MILLIS,
    )
    suspend fun analyzeCloud(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** GLOBAL legacy route: high-risk invoices use the governed tool flow. */
    @Operation(
        prompt = TOOL_PROMPT,
        model = "cloud-invoice-model",
        tools = ["schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeCloudPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = AUTO_PAYMENT_PROMPT,
        model = "cloud-invoice-model",
        tools = ["auto-schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeCloudAutoPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** Explicit contest smoke operation; normal route selection is unchanged. */
    @Operation(
        prompt = ASSESSMENT_PROMPT,
        model = "global-nvidia-invoice-model",
        timeoutMillis = MODEL_ATTEMPT_TIMEOUT_MILLIS,
    )
    suspend fun analyzeGlobalNvidia(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** GLOBAL NVIDIA payment path: the same governed tool flow as LOCAL and EU. */
    @Operation(
        prompt = TOOL_PROMPT,
        model = "global-nvidia-invoice-model",
        tools = ["schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeGlobalNvidiaPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = AUTO_PAYMENT_PROMPT,
        model = "global-nvidia-invoice-model",
        tools = ["auto-schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeGlobalNvidiaAutoPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** Explicit contest smoke operation for the configured EU managed endpoint. */
    @Operation(
        prompt = ASSESSMENT_PROMPT,
        model = "eu-scaleway-invoice-model",
        timeoutMillis = MODEL_ATTEMPT_TIMEOUT_MILLIS,
    )
    suspend fun analyzeEuScaleway(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    /** EU payment path: high-risk invoices use the same governed tool flow as LOCAL. */
    @Operation(
        prompt = TOOL_PROMPT,
        model = "eu-scaleway-invoice-model",
        tools = ["schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeEuScalewayPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment

    @Operation(
        prompt = AUTO_PAYMENT_PROMPT,
        model = "eu-scaleway-invoice-model",
        tools = ["auto-schedule-payment"],
        timeoutMillis = PAYMENT_APPROVAL_TIMEOUT_MILLIS,
    )
    suspend fun analyzeEuScalewayAutoPayment(
        document: ClassifiedDocument<InvoiceDocument>,
    ): InvoiceAssessment
}
