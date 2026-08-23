package dev.giona.ktconf.governance

import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import java.util.concurrent.atomic.AtomicInteger

/**
 * Invocation counter decorator — the stage/security oracle works identically
 * whether the delegate is the deterministic script or a real endpoint.
 *
 * The sovereignty proof relies on it: policy denial happens BEFORE
 * [complete] is called, so a denied RESTRICTED → cloud request leaves the
 * counter unchanged (cloud invocation delta = 0) even when the delegate is
 * real DeepSeek.
 */
class CountingModelProvider(
    internal val delegate: ModelProvider,
) : ModelProvider {

    private val calls = AtomicInteger()

    override fun providerId(): String = delegate.providerId()

    override fun supportsCapability(capability: ProviderCapability): Boolean =
        delegate.supportsCapability(capability)

    override suspend fun complete(request: ModelRequest): ModelResponse {
        calls.incrementAndGet()
        return delegate.complete(request)
    }

    fun invocationCount(): Int = calls.get()
}
