# AGENTS.md — NVIDIA GTC contest branch

This file applies to `contest/gtc-nvidia-submission`.

## Read first

Before editing code, read in this order:

1. `GTC-2026-SUBMISSION.md`
2. `docs/gtc/CURRENT-STATE.md`
3. `docs/gtc/BOARD.md`
4. the exact assigned `docs/gtc/tasks/task-{n}.md`
5. `docs/gtc/ROADMAP.md`
6. `docs/gtc/ARCHITECTURE.md`
7. `docs/gtc/NVIDIA-NEBIUS.md`
8. `docs/gtc/AGENT-GUIDE.md`
9. `docs/gtc/SUBMISSION-CHECKLIST.md`

Then read the existing KTConf `README.md`, `docs/CLAIMS-BOUNDARY.md`, `docs/ARCHITECTURE.md`, and `docs/DEMO-SCRIPT.md` before modifying established behavior.

## Branch mission

Convert the existing typed AI boundaries demo into a real NVIDIA Golden Ticket submission without weakening or destabilizing the KTConf deterministic path.

North-star statement:

> **The model reasons. TramAI governs where it runs and what it may do.**

Target architecture:

```text
LOCAL        -> NVIDIA RTX -> local Nemotron
EU_CLOUD     -> Nebius France -> NVIDIA H200 -> NVIDIA NIM -> Nemotron
GLOBAL_CLOUD -> Build.NVIDIA.com -> hosted Nemotron
```

A real PDF carries trusted classification/residency metadata. Metadata is parsed locally before inference. TramAI authorizes placement. Nemotron may propose a payment, but the existing HIGH/HUMAN_REQUIRED tool policy controls execution.

## Execution protocol

`docs/gtc/BOARD.md` is the operational source of truth.

Before starting work:

1. choose only a task marked `READY`;
2. read its complete `task-{n}.md` contract;
3. ensure its dependencies are satisfied;
4. mark it `IN_PROGRESS` on the board and record the agent/worktree/branch if the workflow supports that metadata;
5. stay inside the task's declared scope and non-goals.

When implementation is complete:

1. run every verification gate required by the task;
2. record sanitized evidence and limitations;
3. update the task status to `REVIEW` or `DONE` as appropriate;
4. update `docs/gtc/BOARD.md`;
5. update `docs/gtc/CURRENT-STATE.md` with the new checkpoint;
6. provide the handoff format defined in `docs/gtc/AGENT-GUIDE.md`.

Do not start a `BLOCKED` task by guessing around its dependencies. Resolve or explicitly revise the dependency first.

## Non-negotiables

- Do not commit secrets.
- Do not weaken existing policy, approval, exactly-once-demo, or audit tests.
- Do not make deterministic CI depend on network/GPU/provider availability.
- Do not fake `EU_CLOUD` semantics in application code if pinned TramAI lacks the concept; implement the governance capability correctly upstream.
- Do not classify a PDF by first sending it to a remote model.
- Do not claim EU/legal sovereignty or regulatory compliance without evidence.
- Do not silently fall back to deterministic providers when a real-provider run was explicitly requested.
- Keep logical model/provider identity separate from actual deployment model IDs.
- Keep provider invocation counters around all routes used for pre-invocation denial proofs.

## Work order

Atomic work is defined in `docs/gtc/tasks/`; milestone context is in `docs/gtc/ROADMAP.md`.

Independent early tasks:

- `task-001` — TramAI regional trust semantics
- `task-002` — GLOBAL Build.NVIDIA.com Nemotron
- `task-003` — LOCAL RTX Nemotron
- `task-004` — Nebius EU NVIDIA NIM
- `task-005` — PDF + trusted metadata

Integration (`task-006`) remains blocked until those independent contracts/proofs are complete.

## Handoff

Every completed task must state exact tests/gates, changed files/commits, evidence, limitations, and follow-on blockers. Update both `docs/gtc/BOARD.md` and `docs/gtc/CURRENT-STATE.md`.

Use the handoff format in `docs/gtc/AGENT-GUIDE.md`.
