package dev.giona.ktconf

import dev.giona.ktconf.demo.DemoResponses
import dev.giona.ktconf.demo.ScriptedProvider
import dev.giona.ktconf.demo.demoScript
import dev.tramai.core.model.Message
import dev.tramai.core.model.MessageRole
import dev.tramai.core.model.ModelRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

/**
 * The deterministic provider must be input-driven, per-workflow, and loud
 * on unknown input — never a magical global response machine.
 */
class ScriptedProviderTest {

    private val provider = ScriptedProvider("test", ::demoScript)

    private fun request(userContent: String, toolResultPresent: Boolean = false): ModelRequest {
        val messages = buildList {
            add(Message(role = MessageRole.USER, content = userContent))
            if (toolResultPresent) {
                add(Message(role = MessageRole.TOOL, content = "tool result"))
            }
        }
        return ModelRequest(model = "invoice-model", messages = messages)
    }

    @Test
    fun `catering invoice maps to the typed assessment`() = runBlocking {
        val response = provider.complete(request("Analyze invoice \"KTCONF-001\" please"))
        assertNotNull(response.content)
        assertEquals(true, response.content.contains("KTConf Catering BV"))
    }

    @Test
    fun `payment invoice without tool result requests the payment tool`() = runBlocking {
        val response = provider.complete(request("KTCONF-PAY-001 needs scheduling"))
        assertNotNull(response.toolCalls)
        assertEquals("schedule-payment", response.toolCalls!!.first().name)
    }

    @Test
    fun `payment invoice with tool result returns the typed assessment`() = runBlocking {
        val response = provider.complete(request("KTCONF-PAY-001", toolResultPresent = true))
        assertNotNull(response.content)
        assertEquals(true, response.content.contains("SCHEDULE_PAYMENT"))
        assertEquals(null, response.toolCalls)
    }

    @Test
    fun `restricted advisory invoice maps to the advisory assessment`() = runBlocking {
        val response = provider.complete(request("KTCONF-RESTRICTED-001 advisory"))
        assertNotNull(response.content)
        assertEquals(true, response.content.contains("ACME Acquisition Advisory"))
    }

    @Test
    fun `unknown invoice is a hard failure`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            provider.complete(request("Analyze invoice UNKNOWN-999"))
        }
    }

    @Test
    fun `per-workflow state - two payment workflows do not share script progress`() = runBlocking {
        // Workflow A is on turn 2 (tool result present), workflow B on turn 1.
        val a = provider.complete(request("KTCONF-PAY-001", toolResultPresent = true))
        val b = provider.complete(request("KTCONF-PAY-001"))
        assertEquals(true, a.content?.contains("SCHEDULE_PAYMENT"))
        assertNotNull(b.toolCalls)
        assertEquals("schedule-payment", b.toolCalls!!.first().name)
        assertEquals(DemoResponses.payAssessment.content, a.content)
        assertEquals(DemoResponses.paymentToolCall.toolCalls, b.toolCalls)
    }

    @Test
    fun `invocation counter counts provider calls`() = runBlocking {
        provider.complete(request("KTCONF-001"))
        provider.complete(request("KTCONF-001"))
        assertEquals(2, provider.invocationCount())
    }
}
