# task-015 — Safe reissue of expired approvals

Status: `DONE`
Track: Post-freeze / workflow resilience
Owner branch: `contest/gtc-nvidia-submission`
Depends on: task-007, task-013

## Objective

Allow an operator to recover a document workflow after its TramAI approval
window expires, without reopening or reusing the expired approval or its
continuation.

The project owns the business-level reissue and history relationship. TramAI
continues to own approval expiry, terminal state, continuation authorization,
and tool execution.

## Required behavior

```text
PENDING approval expires
        ↓
old approval → TIMED_OUT / history EXPIRED
        ↓
operator requests reissue
        ↓
new governed analysis + new approval + new notification
        ↓
old and new history records linked
```

Reissue is allowed only for an expired PDF-backed approval retained in backend
history. It must fail closed for active, approved, denied, unknown, or legacy
workflow approvals.

## Scope

- Add an approval reissue endpoint and clear conflict/not-found responses.
- Use stored trusted invoice metadata/context; never require the original PDF
  bytes, approval token, or continuation token from the client.
- Terminalize the old approval through TramAI's `ApprovalTransition.Timeout`.
- Start a fresh `InvoiceService` analysis so routing, policy, provider
  invocation, approval, and fake-email recording use the existing seams.
- Link old/new document history records and show expiry/reissue events.
- Add a Document History reissue action for expired records.
- Add deterministic coverage for terminalization, reissue linkage, and active
  approval rejection.

## Non-goals

- Do not modify TramAI or reopen a terminal approval.
- Do not reuse a prior continuation, approval token, payment idempotency key,
  or audit chain.
- Do not add persistent storage, automatic retries, or background expiry jobs.
- Do not change payment, authorization, or exactly-once semantics.
- Do not expose approval tokens or raw PDF bytes.

## Acceptance criteria

- [x] Expiry is represented clearly in backend history and the dashboard.
- [x] Reissue is accepted only after TramAI accepts the timeout transition.
- [x] The old approval cannot resume after reissue.
- [x] A reissue creates a new approval and fake approval email when the model
      again requests a high-risk tool.
- [x] Old/new records contain explicit reissue links and readable timeline
      events.
- [x] Reissuing an active or terminal approval returns a safe HTTP conflict;
      unknown approval returns not found.
- [x] Existing deterministic approval, payment, audit, and frontend builds
      remain green.

## Limitations and evidence

History and pending approval context remain in-memory demo state and reset on
restart. Reissue is intentionally an operator action, not an automatic retry.

Verification:

- `./gradlew :app:test --tests dev.giona.ktconf.ApprovalReissueTest --no-daemon --console=plain` — BUILD SUCCESSFUL.
- Full deterministic `./gradlew :app:test --no-daemon --console=plain` — BUILD SUCCESSFUL.
- `npm run build` in `frontend/` — Vite production build successful.

The focused test advances a test clock beyond the TramAI approval expiry,
proves the old approval is terminal and unusable, then proves the replacement
approval/email and old/new history links. No live provider is required.

Handoff: reissue is implemented in the project approval/history/dashboard
layers. TramAI remains unchanged and authoritative for timeout, terminal
approval state, continuation authorization, and tool execution.
