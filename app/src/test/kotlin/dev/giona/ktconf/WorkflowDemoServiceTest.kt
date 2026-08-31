package dev.giona.ktconf

import dev.giona.ktconf.ai.InvoiceAnalysisService
import dev.giona.ktconf.application.InvoiceRoute
import dev.giona.ktconf.application.WorkflowHumanApprovalGateway
import dev.giona.ktconf.domain.InvoiceAction
import dev.giona.ktconf.domain.InvoiceAssessment
import dev.giona.ktconf.domain.InvoiceDocument
import dev.giona.ktconf.domain.InvoiceRisk
import dev.giona.ktconf.observability.GovernanceTelemetry
import dev.giona.ktconf.notifications.FakeEmailService
import dev.giona.ktconf.payments.InMemoryPaymentLedger
import dev.giona.ktconf.workflowdemo.WorkflowDemoService
import dev.giona.ktconf.workflowdemo.WorkflowOutcome
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
        confidence = 0.96,
        rationale = "Deterministic test result",
    )

    private val ai = object : InvoiceAnalysisService {
        override suspend fun analyzeLocal(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun analyzeLocalNvidia(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun analyzeLocalNvidiaPayment(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun analyzeEuScaleway(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun analyzeCloud(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun analyzeGlobalNvidia(document: ClassifiedDocument<InvoiceDocument>) = assessment

        override suspend fun preAssessLocal(document: ClassifiedDocument<InvoiceDocument>) = assessment
    }

    private fun service(serviceAi: InvoiceAnalysisService = ai): WorkflowDemoService = WorkflowDemoService(
        ai = serviceAi,
        approvalGateway = WorkflowHumanApprovalGateway(InMemoryPaymentLedger()),
        email = FakeEmailService(),
        observer = NoOpWorkflowObserver,
        telemetry = GovernanceTelemetry(OpenTelemetry.noop()),
    )

    @Test
    fun `workflow uses the normal cloud route for a public invoice`() {
        val result = runBlocking {
            service().analyze(DemoRequests.typed())
        }

        val completed = (result as WorkflowOutcome.Completed).result
        assertEquals(InvoiceRoute.CLOUD, completed.selectedRoute)
        assertEquals("KTCONF-001", completed.assessment.invoiceId)
    }

    @Test
    fun `workflow uses the normal local route for a restricted invoice`() {
        val result = runBlocking {
            service().analyze(DemoRequests.restricted())
        }

        val completed = (result as WorkflowOutcome.Completed).result
        assertEquals(InvoiceRoute.LOCAL, completed.selectedRoute)
        assertEquals("KTCONF-001", completed.assessment.invoiceId)
    }

    @Test
    fun `trusted amount gate requires approval even when AI recommends review only`() {
        val highValueReviewOnly = assessment.copy(
            invoiceId = "KTCONF-PAY-001",
            amountCents = 1_840_000,
            recommendedAction = InvoiceAction.REVIEW_ONLY,
        )
        val cautiousAi = object : InvoiceAnalysisService by ai {
            override suspend fun preAssessLocal(document: ClassifiedDocument<InvoiceDocument>) = highValueReviewOnly
        }

        val result = runBlocking { service(cautiousAi).analyze(DemoRequests.payment()) }

        val pending = result as WorkflowOutcome.AwaitingApproval
        assertEquals("amount-above-5000-eur", pending.approvalGate)
        assertEquals(InvoiceAction.REVIEW_ONLY, pending.assessment.recommendedAction)
    }

    @Test
    fun `explicit local NVIDIA route still uses the governed payment gate`() {
        val highValue = assessment.copy(
            invoiceId = "KTCONF-PAY-001",
            amountCents = 1_840_000,
            recommendedAction = InvoiceAction.REQUEST_HUMAN_APPROVAL,
        )
        val nvidiaAi = object : InvoiceAnalysisService by ai {
            override suspend fun analyzeLocalNvidia(document: ClassifiedDocument<InvoiceDocument>) = highValue
        }

        val result = runBlocking {
            service(nvidiaAi).analyze(DemoRequests.payment(), InvoiceRoute.LOCAL_NVIDIA)
        }

        val pending = result as WorkflowOutcome.AwaitingApproval
        assertEquals("amount-above-5000-eur", pending.approvalGate)
        assertEquals(InvoiceAction.REQUEST_HUMAN_APPROVAL, pending.assessment.recommendedAction)
    }
}
