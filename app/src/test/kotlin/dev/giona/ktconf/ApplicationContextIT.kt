package dev.giona.ktconf

import dev.giona.ktconf.demo.DeterministicProvider
import dev.giona.ktconf.governance.CountingModelProvider
import dev.giona.ktconf.payments.SchedulePaymentTool
import dev.giona.ktconf.notifications.SendApprovalEmailTool
import dev.tramai.core.model.TramaiTool
import dev.tramai.core.provider.ModelProvider
import dev.tramai.sovereign.SovereignTramaiRuntime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The one-app architecture invariants: exactly one governed runtime,
 * provider routes alive simultaneously, the tool discovered as a
 * Spring bean, and no profile topology.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApplicationContextIT {

    @Autowired
    lateinit var context: org.springframework.context.ApplicationContext

    @Test
    fun `exactly one SovereignTramaiRuntime bean exists`() {
        val runtimes = context.getBeansOfType(SovereignTramaiRuntime::class.java)
        assertEquals(1, runtimes.size, "expected exactly one SovereignTramaiRuntime bean")
    }

    @Test
    fun `both local and cloud providers coexist in one context`() {
        val providers = context.getBeansOfType(ModelProvider::class.java).values.toList()
        assertEquals(5, providers.size, "expected local + local NVIDIA + EU Scaleway + cloud + global NVIDIA provider beans")
        val ids = providers.map { it.providerId() }.toSet()
        assertEquals(setOf("local-provider", "local-nvidia-provider", "eu-scaleway-provider", "cloud-provider", "global-nvidia-provider"), ids)
    }

    @Test
    fun `app starts without any profile topology`() {
        val profiles = context.environment.activeProfiles
        assertTrue(profiles.isEmpty(), "no demo/broken/cloud-routing/real profiles expected, found ${profiles.toList()}")
    }

    @Test
    fun `SchedulePaymentTool is discovered as a TramaiTool Spring bean`() {
        val tool = context.getBean("schedulePaymentTool")
        assertNotNull(tool)
        assertTrue(tool is TramaiTool<*, *>)
        assertTrue(tool is SchedulePaymentTool)
        assertEquals("schedule-payment", (tool as TramaiTool<*, *>).name)
    }

    @Test
    fun `SendApprovalEmailTool is discovered as a TramaiTool Spring bean`() {
        val tool = context.getBean("sendApprovalEmailTool")
        assertTrue(tool is SendApprovalEmailTool)
        assertEquals("send-approval-email", tool.name)
    }

    @Test
    fun `both providers are deterministic in a clean environment`() {
        // KTCONF_DEMO_* (local AND cloud) are unset in CI/tests, so both
        // identities must be deterministic (never network clients).
        val local = context.getBean("localProvider") as ModelProvider
        val cloud = context.getBean("cloudProvider") as ModelProvider
        assertTrue(local is CountingModelProvider)
        assertTrue((local as CountingModelProvider).delegate is DeterministicProvider, "local provider must be deterministic when no real-model env is set")
        assertTrue(cloud is CountingModelProvider)
        assertTrue((cloud as CountingModelProvider).delegate is DeterministicProvider)
        val global = context.getBean("globalNvidiaProvider") as ModelProvider
        assertTrue(global is CountingModelProvider)
        assertTrue((global as CountingModelProvider).delegate is DeterministicProvider)
        val localNvidia = context.getBean("localNvidiaProvider") as ModelProvider
        assertTrue(localNvidia is CountingModelProvider)
        assertTrue((localNvidia as CountingModelProvider).delegate is DeterministicProvider)
        val euScaleway = context.getBean("euScalewayProvider") as ModelProvider
        assertTrue(euScaleway is CountingModelProvider)
        assertTrue((euScaleway as CountingModelProvider).delegate is DeterministicProvider)
    }
}
