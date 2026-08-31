# task-007 — Governed payment on the LOCAL NVIDIA execution boundary

Status: `REVIEW`
Track: G
Milestone: M6
Depends on: task-006
Blocks: task-008

## Objective

Reuse the existing governed payment flow so a real local model running on NVIDIA RTX can propose `schedule-payment`, while TramAI remains the authority that requires human approval and enforces demo-scoped exactly-once execution.

## Scope

- Feed the trusted local-only payment PDF into the existing payment workflow without bypassing the current tool metadata.
- Keep `schedule-payment` as `HIGH` risk, `HUMAN_REQUIRED`, write-side-effect, fully audited.
- Demonstrate suspension before side effect, approval/resume, duplicate rejection, denial, and evidence.
- Ensure model text/prompt cannot override approval requirements.

## Acceptance criteria

- [x] Real Qwen output on the NVIDIA RTX local boundary leads to a payment proposal.
- [x] Payment count remains 0 at suspension.
- [x] Approval changes payment count 0 -> 1.
- [x] Duplicate approval/resume is rejected and count remains 1.
- [x] Denial leaves count 0 and continuation is refused.
- [x] Tool policy is enforced outside model output/prompting.
- [x] Hash-chained audit records cover the real Qwen contest workflow.
- [x] Existing deterministic payment oracle remains green.
- [x] The canonical contest entrypoint is `/invoices/analyze-pdf`; the separate workflow-demo approval route is not a contest proof.
- [x] PDF approval responses preserve trusted metadata and selected route.

## Handoff

Provide the end-to-end sequence, sanitized outputs, tool metadata proof, exact counters, audit event sequence, changed files, and any model-output normalization required.

Implementation evidence: `analyzeLocalNvidiaPayment` declares the existing
`schedule-payment` tool with `HIGH`/`HUMAN_REQUIRED` metadata and uses the
existing TramAI approval/resume path. The deterministic suite proves
suspension at payment count 0, approval 0 -> 1, duplicate rejection, denial,
and valid audit-chain evidence. The canonical PDF response preserves
`RESTRICTED`, `LOCAL_ONLY`, `LOCAL_NVIDIA`, and the approval/tool identifiers.
The opt-in real command is `./scripts/gtc-local-nvidia-payment-smoke`, which
uploads `payment-local-invoice.pdf` and checks the model catalog, exact-once
counter, provider invocation delta, and approval audit lifecycle.

The canonical PDF denial test additionally proves payment remains at `0` and
later continuation is rejected with HTTP `409`. The assessment prompt states
the amount-based risk/action rule for tool-free operations and the explicit
post-tool `SCHEDULE_PAYMENT` transition.

Real closure evidence is recorded in
`docs/gtc/evidence/local-nvidia-payment-smoke.md`: Qwen
`qwen/qwen3.8-27b` on the local NVIDIA boundary produced the payment proposal,
TramAI suspended it, approval executed the payment once, duplicate approval
returned `409`, and the audit chain verified. Nemotron was evaluated with two
variants; both produced typed assessments but did not reliably request the
tool under the combined structured-output/tool contract. No Nemotron payment
claim is made. This model substitution does not change the TramAI governance
semantics or the LOCAL trust zone.
