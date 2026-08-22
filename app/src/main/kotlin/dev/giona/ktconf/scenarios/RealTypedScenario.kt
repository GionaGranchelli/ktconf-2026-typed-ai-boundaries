package dev.giona.ktconf.scenarios

import dev.giona.ktconf.ai.RealInvoiceAnalysisService
import dev.giona.ktconf.domain.DemoInvoices
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.runtime.RealModelRuntimeFactory
import dev.tramai.core.exception.StructuredOutputException

/**
 * typed --real: the same typed input/output contract against an actual LLM.
 *
 * Configuration comes from the environment (never hardcoded, never
 * printed):
 *   KTCONF_DEMO_LOCAL_BASE_URL  — OpenAI-compatible endpoint the operator
 *                                 intentionally treats as LOCAL (Ollama on
 *                                 the laptop, private LAN, self-hosted)
 *   KTCONF_DEMO_LOCAL_MODEL     — model name
 *   KTCONF_DEMO_LOCAL_API_KEY   — optional (local servers usually need none)
 *
 * At runtime, a structured rejection is a legitimate safe outcome. For the
 * conference success path, typed --real must produce a typed result; if it
 * rejects on stage, treat the live-model demo as failed and continue with
 * the deterministic scenarios. Default demo execution is unaffected and
 * stays deterministic.
 */
class RealTypedScenario(
    private val env: Map<String, String> = System.getenv(),
    private val factory: RealModelRuntimeFactory = RealModelRuntimeFactory(),
) {

    data class Config(
        val baseUrl: String,
        val model: String,
        val apiKey: String,
    )

    fun config(): Config {
        val baseUrl = env["KTCONF_DEMO_LOCAL_BASE_URL"]
        val model = env["KTCONF_DEMO_LOCAL_MODEL"]
        require(!baseUrl.isNullOrBlank()) {
            "typed --real requires KTCONF_DEMO_LOCAL_BASE_URL (OpenAI-compatible endpoint)"
        }
        require(!model.isNullOrBlank()) {
            "typed --real requires KTCONF_DEMO_LOCAL_MODEL"
        }
        return Config(baseUrl = baseUrl, model = model, apiKey = env["KTCONF_DEMO_LOCAL_API_KEY"] ?: "none")
    }

    suspend fun run(): RealTypedResult {
        val cfg = config()
        factory.real(cfg.baseUrl, cfg.apiKey, cfg.model).use { runtime ->
            val service = runtime.runtime.create(RealInvoiceAnalysisService::class)
            return try {
                RealTypedResult.Success(service.analyze(DemoInvoices.catering))
            } catch (e: StructuredOutputException) {
                RealTypedResult.Rejected(e)
            }
        }
    }
}

sealed interface RealTypedResult {
    data class Success(val assessment: InvoiceAssessment) : RealTypedResult
    data class Rejected(val failure: StructuredOutputException) : RealTypedResult
}
