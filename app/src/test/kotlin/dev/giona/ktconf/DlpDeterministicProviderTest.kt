package dev.giona.ktconf

import dev.giona.ktconf.demo.DeterministicProvider
import dev.giona.ktconf.demo.cloudScript
import dev.tramai.core.model.Message
import dev.tramai.core.model.ModelRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DlpDeterministicProviderTest {

    @Test
    fun `deterministic DLP fixture returns raw sensitive values before TramAI DLP`() = runBlocking {
        val provider = DeterministicProvider("eu-scaleway-provider", ::cloudScript)

        val response = provider.complete(
            ModelRequest(
                model = "eu-scaleway-invoice-model",
                messages = listOf(Message.text("""{"documentId":"KTCONF-DLP-001"}""")),
                operationMethod = "analyzeDocument",
            ),
        )

        assertTrue(response.content.contains("finance@example-confidential.eu"))
        assertTrue(response.content.contains("NL91ABNA0417164300"))
    }
}
