# task-007 — Governed payment on real Nemotron path

Status: `BLOCKED`
Track: G
Milestone: M6
Depends on: task-006
Blocks: task-008

## Objective

Reuse the existing governed payment flow so a real Nemotron-backed workflow can propose `schedule-payment`, while TramAI remains the authority that requires human approval and enforces demo-scoped exactly-once execution.

## Scope

- Feed the real document/NVIDIA assessment into the existing payment workflow without bypassing the current tool metadata.
- Keep `schedule-payment` as `HIGH` risk, `HUMAN_REQUIRED`, write-side-effect, fully audited.
- Demonstrate suspension before side effect, approval/resume, duplicate rejection, denial, and evidence.
- Ensure model text/prompt cannot override approval requirements.

## Acceptance criteria

- [ ] Real Nemotron output can lead to a payment proposal.
- [ ] Payment count remains 0 at suspension.
- [ ] Approval changes payment count 0 -> 1.
- [ ] Duplicate approval/resume is rejected and count remains 1.
- [ ] Denial leaves count 0 and continuation is refused.
- [ ] Tool policy is enforced outside model output/prompting.
- [ ] Hash-chained audit records cover the real contest workflow.
- [ ] Existing deterministic payment oracle remains green.

## Handoff

Provide the end-to-end sequence, sanitized outputs, tool metadata proof, exact counters, audit event sequence, changed files, and any model-output normalization required.
