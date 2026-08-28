package dev.giona.ktconf.api

import dev.giona.ktconf.application.ApprovalService
import dev.giona.ktconf.application.DenyOutcome
import dev.giona.ktconf.application.EvidenceResult
import dev.giona.ktconf.application.EvidenceService
import dev.giona.ktconf.domain.InvoiceAssessment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.slf4j.LoggerFactory

/**
 * Approval lifecycle over HTTP. The challenge token stays server-side in
 * the pending-approval registry — the client only ever holds the id.
 */
@RestController
@RequestMapping("/approvals")
class ApprovalController(
    private val approvals: ApprovalService,
    private val evidence: EvidenceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{approvalId}/approve")
    suspend fun approve(@PathVariable approvalId: String): InvoiceAssessment {
        log.info("Approval requested: approvalId={}", approvalId)
        return approvals.approve(approvalId)
    }

    @PostMapping("/{approvalId}/deny")
    suspend fun deny(@PathVariable approvalId: String): DenyOutcome {
        log.info("Denial requested: approvalId={}", approvalId)
        return approvals.deny(approvalId)
    }

    @GetMapping("/{approvalId}/evidence")
    suspend fun evidence(@PathVariable approvalId: String): EvidenceResult {
        log.info("Evidence requested: approvalId={}", approvalId)
        return evidence.evidenceFor(approvalId)
    }
}
