package dev.giona.ktconf.governance

import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory

/**
 * Provider adapter that substitutes the logical model name with the actual
 * model identifier served by the endpoint.
 *
 * The logical route in application.yml is `local-invoice-model`; the real
 * endpoint (e.g. Ollama) expects its own model id (e.g.
 * `gemma-4-12b-it:q5_k_m`). TramAI sends `request.model` verbatim, so the
 * alias swaps it before the delegate sees the request.
 *
 * No sovereign configuration is touched — this is purely a provider adapter.
 */
class ModelAliasProvider(
    internal val delegate: ModelProvider,
    internal val actualModel: String,
    private val providerIdOverride: String? = null,
) : ModelProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val calls = AtomicInteger()

    override fun providerId(): String = providerIdOverride ?: delegate.providerId()

    override fun supportsCapability(capability: ProviderCapability): Boolean =
        delegate.supportsCapability(capability)

    override suspend fun complete(request: ModelRequest): ModelResponse {
        val invocation = calls.incrementAndGet()
        log.info(
            "Calling model provider: providerId={}, logicalModel={}, actualModel={}, invocation={}",
            providerId(),
            request.model,
            actualModel,
            invocation,
        )
        return try {
            delegate.complete(request.copy(model = actualModel)).also {
                log.info(
                    "Model provider call succeeded: providerId={}, actualModel={}, invocation={}",
                    providerId(),
                    actualModel,
                    invocation,
                )
            }
        } catch (failure: Throwable) {
            log.warn(
                "Model provider call failed: providerId={}, actualModel={}, invocation={}, failureType={}, message={}",
                providerId(),
                actualModel,
                invocation,
                failure::class.simpleName,
                failure.message,
            )
            throw failure
        }
    }

    fun invocationCount(): Int = calls.get()
}
