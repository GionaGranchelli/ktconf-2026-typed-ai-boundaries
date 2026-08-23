package dev.giona.ktconf

import dev.giona.ktconf.governance.CountingModelProvider
import dev.tramai.core.model.FinishReason
import dev.tramai.core.model.Message
import dev.tramai.core.model.MessageRole
import dev.tramai.core.model.ModelRequest
import dev.tramai.core.model.ModelResponse
import dev.tramai.core.provider.ModelProvider
import dev.tramai.core.provider.ProviderCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The counter decorator must work independently of the delegate type —
 * deterministic OR real — and count only actual provider invocations.
 */
class CountingModelProviderTest {

    private val request = ModelRequest(
        model = "cloud-invoice-model",
        messages = listOf(Message(role = MessageRole.USER, content = "hi")),
    )

    private class FakeProvider : ModelProvider {
        var completed = 0
        override fun providerId() = "cloud-provider"
        override fun supportsCapability(capability: ProviderCapability) = true
        override suspend fun complete(request: ModelRequest): ModelResponse {
            completed++
            return ModelResponse(content = "ok", finishReason = FinishReason.STOP)
        }
    }

    @Test
    fun `counter increments per invocation and delegates the request`() {
        val delegate = FakeProvider()
        val counting = CountingModelProvider(delegate)

        assertEquals(0, counting.invocationCount())
        runBlocking { counting.complete(request) }
        runBlocking { counting.complete(request) }

        assertEquals(2, counting.invocationCount())
        assertEquals(2, delegate.completed, "delegate must receive every counted invocation")
        assertEquals("cloud-provider", counting.providerId())
        assertTrue(counting.supportsCapability(ProviderCapability.TOOL_CALLING))
    }

    @Test
    fun `no invocation means no count`() {
        val counting = CountingModelProvider(FakeProvider())
        assertEquals(0, counting.invocationCount())
    }
}

private fun runBlocking(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
