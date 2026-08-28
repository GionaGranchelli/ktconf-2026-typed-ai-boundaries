package dev.giona.ktconf.api

import dev.giona.ktconf.domain.AnalyzeInvoiceRequest
import dev.giona.ktconf.workflowdemo.WorkflowInvoiceResult
import dev.giona.ktconf.workflowdemo.WorkflowDemoService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Isolated TramAI orchestration demo; deliberately separate from /invoices. */
@RestController
@RequestMapping("/workflow-demo")
class WorkflowDemoController(private val workflow: WorkflowDemoService) {
    @PostMapping("/analyze")
    suspend fun analyze(@RequestBody request: AnalyzeInvoiceRequest): WorkflowInvoiceResult = workflow.analyze(request)
}
