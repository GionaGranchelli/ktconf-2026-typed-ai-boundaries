package dev.giona.ktconf

import dev.giona.ktconf.api.ErrorResponse
import dev.giona.ktconf.application.ReissueOutcome
import dev.giona.ktconf.notifications.FakeEmailService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ApprovalReissueTest.TestClockConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApprovalReissueTest {

    @Autowired lateinit var rest: TestRestTemplate
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var email: FakeEmailService

    @Test
    fun `expired PDF approval becomes terminal and creates a fresh linked approval`() {
        val started = mockMvc.perform(
            multipart("/invoices/analyze-pdf")
                .file(MockMultipartFile("file", "payment-local-invoice.pdf", "application/pdf", fixture())),
        ).andReturn()
        val response = mockMvc.perform(asyncDispatch(started)).andReturn().response
        assertEquals(HttpStatus.ACCEPTED.value(), response.status)
        val oldApprovalId = Regex("\\\"approvalId\\\":\\\"([^\\\"]+)").find(response.contentAsString)!!.groupValues[1]

        val active = rest.exchange(
            "/approvals/$oldApprovalId/reissue",
            HttpMethod.POST,
            HttpEntity(null, HttpHeaders()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, active.statusCode)
        assertEquals("approval-reissue-not-allowed", active.body!!.code)

        clock.advanceSeconds(11 * 60)
        val reissued = rest.exchange(
            "/approvals/$oldApprovalId/reissue",
            HttpMethod.POST,
            HttpEntity(null, HttpHeaders()),
            ReissueOutcome::class.java,
        )
        assertEquals(HttpStatus.OK, reissued.statusCode)
        val result = assertNotNull(reissued.body)
        assertEquals("REISSUED_AWAITING_APPROVAL", result.status)
        assertNotNull(result.newApprovalId)
        assertNotEquals(oldApprovalId, result.newApprovalId)
        assertEquals(2, email.count())

        val oldRecord = rest.getForObject(
            "/governance/documents/${result.previousDocumentId}",
            Map::class.java,
        )!!
        assertEquals("EXPIRED", oldRecord["status"])
        assertEquals(result.documentId, oldRecord["reissuedToDocumentId"])

        val newRecord = rest.getForObject(
            "/governance/documents/${result.documentId}",
            Map::class.java,
        )!!
        assertEquals(oldApprovalId, newRecord["reissuedFromApprovalId"])
        assertEquals("AWAITING_APPROVAL", newRecord["status"])

        val oldApproval = rest.exchange(
            "/approvals/$oldApprovalId/approve",
            HttpMethod.POST,
            HttpEntity(null, HttpHeaders()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, oldApproval.statusCode)

        val duplicateReissue = rest.exchange(
            "/approvals/$oldApprovalId/reissue",
            HttpMethod.POST,
            HttpEntity(null, HttpHeaders()),
            ErrorResponse::class.java,
        )
        assertEquals(HttpStatus.CONFLICT, duplicateReissue.statusCode)
    }

    private fun fixture(): ByteArray =
        requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/payment-local-invoice.pdf")).readBytes()

    @TestConfiguration(proxyBeanMethods = false)
    class TestClockConfiguration {
        @Bean
        @Primary
        fun sovereignClock(): MutableClock = MutableClock(Instant.parse("2026-09-02T12:00:00Z"))
    }
}

class MutableClock(initial: Instant) : Clock() {
    private val current = AtomicReference(initial)

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current.get()

    fun advanceSeconds(seconds: Long) {
        current.updateAndGet { it.plusSeconds(seconds) }
    }
}
