package dev.giona.ktconf

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves the application exposes the pinned TramAI native, publish-safe pack. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SovereignEvidencePackIT {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `native sovereign evidence pack exposes runtime posture without sensitive fields`() {
        val response = rest.getForEntity("/governance/sovereign-evidence", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val pack: JsonNode = objectMapper.readTree(response.body)
        assertEquals(1, pack["schemaVersion"].asInt())
        assertTrue(pack["allowedModels"].map { it.asText() }.contains("local-nvidia-invoice-model"))
        assertTrue(pack["allowedProviders"].map { it.asText() }.contains("eu-scaleway-provider"))
        assertEquals("LOCAL", pack["providerZones"]["local-nvidia-provider"].asText())
        assertEquals("EU_CLOUD", pack["providerZones"]["eu-scaleway-provider"].asText())
        assertEquals("GLOBAL_CLOUD", pack["providerZones"]["global-nvidia-provider"].asText())
        val serialized = response.body.orEmpty().lowercase()
        assertTrue("prompt" !in serialized)
        assertTrue("/home/" !in serialized)
        assertTrue("api_key" !in serialized)
        assertTrue("secret" !in serialized)
    }
}
