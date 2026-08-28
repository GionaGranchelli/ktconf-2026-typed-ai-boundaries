package dev.giona.ktconf

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.giona.ktconf.workflowdemo.WorkflowDemoService
import dev.tramai.core.model.ClassifiedDocument
import dev.tramai.orchestration.NoOpWorkflowObserver
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowDemoServiceTest {
    private val assessment = InvoiceAssessment(
        invoiceId = "KTCONF-001",
        supplierName = "KTConf Catering BV",
        amountCents = 42_830,
        currency = "EUR",
        risk = InvoiceRisk.LOW,
        recommendedAction = InvoiceAction.REVIEW_ONLY,
        rationale = "Deterministic test result",
    )

    private val ai = object : InvoiceAnalysisService {
        override suspend fun analyzeLocal(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun analyzeCloud(document: ClassifiedDocument<InvoiceDocument>) = assessment
    }

    @Test
    fun `workflow uses the normal cloud route for a public invoice`() {
        val result = runBlocking {
            WorkflowDemoService(ai, NoOpWorkflowObserver, GovernanceTelemetry(OpenTelemetry.noop())).analyze(DemoRequests.typed())
        }

        assertEquals(InvoiceRoute.CLOUD, result.selectedRoute)
        assertEquals("KTCONF-001", result.assessment.invoiceId)
    }

    @Test
    fun `workflow uses the normal local route for a restricted invoice`() {
        val result = runBlocking {
            WorkflowDemoService(ai, NoOpWorkflowObserver, GovernanceTelemetry(OpenTelemetry.noop())).analyze(DemoRequests.restricted())
        }

        assertEquals(InvoiceRoute.LOCAL, result.selectedRoute)
        assertEquals("KTCONF-001", result.assessment.invoiceId)
    }
}
