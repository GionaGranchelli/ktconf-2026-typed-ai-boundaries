package dev.giona.ktconf.application

import dev.tramai.core.approval.ApprovalToken
import dev.tramai.core.exception.ApprovalSuspendedException
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/**
 * Application-owned registry of pending approvals.
 *
 * TramAI stores hold the durable-ish approval state, but the presented
 * challenge token cannot be reconstructed from its digest — so the
 * application retains [PendingApproval] server-side, keyed by approval ID.
 * The token is NEVER exposed over REST.
 *
 * Entries survive completion/denial so evidence stays resolvable per
 * workflow. Multiple workflows are isolated within one process; state is
 * intentionally in-memory and not durable across restart (demo scope —
 * the TramAI stores share the same property).
 */
@Component
class PendingApprovalRegistry {

    enum class State { PENDING, COMPLETED, DENIED, REJECTED }

    data class PendingApproval(
        val approvalId: String,
        val workflowRunId: String,
        val continuationVersion: Long,
        val presentedToken: ApprovalToken,
        val toolName: String,
        val state: State = State.PENDING,
    )

    private val approvals = ConcurrentHashMap<String, PendingApproval>()

    fun register(suspension: ApprovalSuspendedException): PendingApproval =
        PendingApproval(
            approvalId = suspension.approvalId,
            workflowRunId = suspension.workflowRunId,
            continuationVersion = suspension.continuationVersion,
            presentedToken = suspension.challenge.token,
            toolName = suspension.toolName,
        ).also { approvals[it.approvalId] = it }

    fun get(approvalId: String): PendingApproval? = approvals[approvalId]

    fun require(approvalId: String): PendingApproval =
        get(approvalId) ?: throw ApprovalNotFoundException(approvalId)

    fun complete(approvalId: String, state: State) {
        approvals[approvalId] = require(approvalId).copy(state = state)
    }
}

class ApprovalNotFoundException(approvalId: String) :
    RuntimeException("No pending or completed approval with id '$approvalId'")
