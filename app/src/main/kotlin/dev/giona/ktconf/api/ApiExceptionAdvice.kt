package dev.giona.ktconf.api

import dev.giona.ktconf.application.ApprovalNotFoundException
import dev.giona.ktconf.application.WorkflowApprovalStateException
import dev.giona.ktconf.application.ApprovalReissueNotAllowedException
import dev.giona.ktconf.application.ApprovalReissueNotFoundException
import dev.tramai.core.exception.ApprovalAuthorizationException
import dev.tramai.core.exception.ApprovalStoreConflictException
import dev.tramai.core.exception.ApprovalStoreNotConsumableException
import dev.tramai.core.exception.ApprovalStoreTokenRejectedException
import dev.tramai.core.exception.ApprovalTokenRejectedException
import dev.tramai.core.exception.IllegalApprovalTransitionException
import dev.tramai.core.exception.PolicyViolationException
import dev.tramai.core.exception.StructuredOutputException
import dev.tramai.orchestration.WorkflowGateRejectedException
import dev.giona.ktconf.pdf.InvalidTrustedPdfException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Small, readable error mapping — no enterprise error framework. curl
 * output should be self-explanatory on stage.
 */
@RestControllerAdvice
class ApiExceptionAdvice {

    @ExceptionHandler(WorkflowGateRejectedException::class)
    fun workflowGateRejected(e: WorkflowGateRejectedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("workflow-gate-rejected", e.message ?: "Workflow gate rejected"))

    @ExceptionHandler(PolicyViolationException::class)
    fun policyDenied(e: PolicyViolationException): ResponseEntity<ErrorResponse> {
        val decision = e.decision
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(code = decision.reasonCode, message = decision.reason),
        )
    }

    @ExceptionHandler(StructuredOutputException::class)
    fun structuredRejected(e: StructuredOutputException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(
                code = "structured-output-rejected",
                message = e.validationError ?: e.message ?: "Structured output parsing failed",
            ),
        )

    @ExceptionHandler(
        ApprovalTokenRejectedException::class,
        ApprovalAuthorizationException::class,
        ApprovalStoreConflictException::class,
        ApprovalStoreNotConsumableException::class,
        ApprovalStoreTokenRejectedException::class,
        IllegalApprovalTransitionException::class,
        WorkflowApprovalStateException::class,
    )
    fun approvalRejected(e: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = "approval-rejected", message = e.message ?: "Approval rejected"),
        )

    @ExceptionHandler(ApprovalNotFoundException::class)
    fun approvalNotFound(e: ApprovalNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(code = "approval-not-found", message = e.message ?: "Approval not found"),
        )

    @ExceptionHandler(ApprovalReissueNotFoundException::class)
    fun approvalReissueNotFound(e: ApprovalReissueNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(code = "approval-reissue-not-found", message = e.message ?: "Approval reissue source not found"),
        )

    @ExceptionHandler(ApprovalReissueNotAllowedException::class)
    fun approvalReissueNotAllowed(e: ApprovalReissueNotAllowedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = "approval-reissue-not-allowed", message = e.message ?: "Approval cannot be reissued"),
        )

    @ExceptionHandler(IllegalStateException::class)
    fun illegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(code = "demo-invariant-violated", message = e.message ?: "Demo invariant violated"),
        )

    @ExceptionHandler(InvalidTrustedPdfException::class)
    fun invalidPdf(e: InvalidTrustedPdfException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "invalid-pdf-metadata", message = e.message ?: "Invalid PDF input"),
        )
}

data class ErrorResponse(
    val code: String,
    val message: String,
)
