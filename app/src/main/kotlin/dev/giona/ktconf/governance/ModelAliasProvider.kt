package dev.giona.ktconf.governance

import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability

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
    private val delegate: ModelProvider,
    private val actualModel: String,
) : ModelProvider {

    override fun providerId(): String = delegate.providerId()

    override fun supportsCapability(capability: ProviderCapability): Boolean =
        delegate.supportsCapability(capability)

    override suspend fun complete(request: ModelRequest): ModelResponse =
        delegate.complete(request.copy(model = actualModel))
}
