package dev.giona.ktconf.demo

import dev.tramai.core.model.MessageRole
import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic model provider — the demo's stand-in for a real LLM.
 *
 * Input-driven and per-workflow: the script is chosen from the invoice id
 * in the user message, and the PAY flow branches on whether the engine has
 * already appended the tool result to the message history. There is NO
 * global call counter driving responses, so concurrent requests cannot
 * consume each other's scripted sequence.
 *
 * Unknown input is a HARD failure — a deterministic provider must never
 * silently fall back (that would make it magical test machinery).
 *
 * [invocationCount] exists only for the cloud-denial proof: the cloud
 * provider must never be invoked for RESTRICTED data.
 */
class ScriptedProvider(
    private val providerId: String,
    private val script: (invoiceId: String, toolResultPresent: Boolean) -> ModelResponse,
) : ModelProvider {

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
            ?: error("ScriptedProvider: no deterministic response for request (no known invoice id in user message)")
        val toolResultPresent = request.messages.any { it.role == MessageRole.TOOL }
        callCount.incrementAndGet()
        return script(invoiceId, toolResultPresent)
    }

    /** Invocation counter — used to prove policy denials happen BEFORE invocation. */
    fun invocationCount(): Int = callCount.get()

    companion object {
        /** Longest ids first so prefix-ish matches resolve deterministically. */
        val KNOWN_INVOICE_IDS = listOf("KTCONF-PAY-001", "KTCONF-RESTRICTED-001", "KTCONF-001")
    }
}

/** The governed profiles' script: one coherent application, three invoices. */
fun demoScript(invoiceId: String, toolResultPresent: Boolean): ModelResponse = when {
    invoiceId == "KTCONF-PAY-001" && !toolResultPresent -> DemoResponses.paymentToolCall
    invoiceId == "KTCONF-PAY-001" -> DemoResponses.payAssessment
    invoiceId == "KTCONF-RESTRICTED-001" -> DemoResponses.restrictedAdvisoryAssessment
    invoiceId == "KTCONF-001" -> DemoResponses.cateringAssessment
    else -> error("No deterministic response for invoice $invoiceId")
}
