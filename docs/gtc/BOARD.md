# GTC execution board

Branch: `contest/gtc-nvidia-submission`

This board is the operational source of truth for agent execution. `ROADMAP.md` explains the milestone structure; this file tracks what can be picked up now.

## Rules

- One agent owns one task file at a time.
- Before starting, read `AGENTS.md`, `GTC-2026-SUBMISSION.md`, `docs/gtc/CURRENT-STATE.md`, and the assigned task file.
- Do not widen scope without updating the task file and board first.
- Every task must end with evidence: tests, commands, observed outputs, limitations, and a handoff note.
- Never commit credentials, tokens, account IDs, private endpoints, or secrets.
- Preserve the deterministic KTConf path unless the task explicitly says otherwise.
- A task moves to `DONE` only when its acceptance criteria are demonstrably satisfied.

## Status legend

- `READY` — can start now.
- `BLOCKED` — dependency or external access required.
- `IN_PROGRESS` — claimed by one agent.
- `REVIEW` — implementation complete; evidence/review pending.
- `DONE` — accepted.

## Board

| Task | Status | Track | Depends on | Objective |
|---|---|---|---|---|
| [task-001](tasks/task-001.md) | DONE | A | — | Establish native `EU_CLOUD`/regional trust semantics in TramAI |
| [task-002](tasks/task-002.md) | REVIEW | B | — | Prove GLOBAL Build.NVIDIA.com Nemotron inference |
| [task-003](tasks/task-003.md) | DONE | C | — | Prove LOCAL RTX + Nemotron inference |
| [task-004](tasks/task-004.md) | REVIEW | D | — | Prove Nebius France + NVIDIA H200 + NIM path |
| [task-005](tasks/task-005.md) | REVIEW | E | — | Add fail-closed real PDF + trusted metadata ingestion |
| [task-006](tasks/task-006.md) | BLOCKED | F | 001,002,003,004,005 | Integrate three governed NVIDIA execution boundaries |
| [task-007](tasks/task-007.md) | BLOCKED | G | 006 | Adapt payment approval flow to real Nemotron path |
| [task-008](tasks/task-008.md) | BLOCKED | H | 006,007 | Build contest evidence pack and adversarial/misroute proof suite |
| [task-011](tasks/task-011.md) | REVIEW | UI | 005 implementation; final binds 006–008 | Build Vue/Vite GTC Governance Console |
| [task-009](tasks/task-009.md) | BLOCKED | I | 008,011 | Build `scripts/gtc-demo` and 60-second deterministic recording flow |
| [task-010](tasks/task-010.md) | BLOCKED | J | 009 | Final scoring review, freeze, README/submission assets |

## Evidence checkpoint

- task-003 is `DONE`: local smoke passed with RTX 3060 / driver 580.173.02,
  llama.cpp 9986, Nemotron `nvidia/nemotron-3-nano-4b`, direct HTTP 200, typed
  application HTTP 200, `selectedRoute=LOCAL_NVIDIA`, and invocation count 1.
- task-004 remains `REVIEW`: a real Nebius endpoint is now provisioned in the
  existing `eu-west1` project with H200 capacity and is still `STARTING`.
  Managed URL and endpoint ID are recorded in `CURRENT-STATE.md`; direct NIM
  inference is not claimed until startup completes.
- task-005 is `REVIEW`: PDFBox ingestion, documented metadata contract, three
  synthetic fixtures, and fail-closed parser tests are complete. The focused
  parser suite passed 5/5 and the full app suite passed. A multipart endpoint
  counter test remains follow-on hardening.
- task-011 is `REVIEW` on `task/011-gtc-governance-console`: Vue/Vite multi-view
  GTC Governance Console built and verified (`npm run build` passes 28 modules,
  0 errors, 108 kB JS / 23.5 kB CSS). Live Governance hero view includes
  WorkflowTrace timeline, fail-closed Policy Denial proof, fail-closed Replay
  protection proof, AuditTimeline, and ProofStrip hero summary. Sourced from
  real backend endpoints (`/invoices/analyze-pdf`, `/approvals/*`, `/governance/*`).

Tasks 006–010 remain on their dependency chain. Task-011 can progress in
parallel because the task-005 API implementation already exists, but task-009
cannot start until both backend evidence (008) and the governance console (011)
are ready.

## Critical path

```text
001 ─┐
002 ─┤
003 ─┤
004 ─┼─> 006 -> 007 -> 008 ─┐
005 ─┘       \              ├─> 009 -> 010
              └-> 011 ------┘
```

The UI deliberately starts before backend integration is complete. It must not
invent final route/evidence semantics while those contracts are still moving.

## Global definition of done

The branch is submission-ready only when all of the following are true:

1. A real PDF is accepted only after trusted classification/residency metadata is parsed locally.
2. The same application exposes three governed NVIDIA execution boundaries: `LOCAL`, `EU_CLOUD`, `GLOBAL_CLOUD`.
3. At least one real successful inference is proven for each boundary.
4. A disallowed route is denied before provider invocation and the corresponding provider counter delta is exactly `0`.
5. Nemotron can propose the governed payment action but cannot execute it without TramAI's human-approval gate.
6. Approval resumes exactly once in demo scope; duplicate approval is rejected.
7. Hash-chained audit evidence verifies.
8. The deterministic offline path remains green.
9. No secrets appear in source, logs, screenshots, video, evidence, or Git history.
10. A reviewer can understand the project and reproduce the primary proof from the repository without reading chat history.
11. The Governance Console visualizes backend evidence without fabricating policy, route, counter, approval, or audit state.
