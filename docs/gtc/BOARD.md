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
| [task-001](tasks/task-001.md) | READY | A | — | Establish native `EU_CLOUD`/regional trust semantics in TramAI |
| [task-002](tasks/task-002.md) | READY | B | — | Prove GLOBAL Build.NVIDIA.com Nemotron inference |
| [task-003](tasks/task-003.md) | READY | C | — | Prove LOCAL RTX + Nemotron inference |
| [task-004](tasks/task-004.md) | READY | D | — | Prove Nebius France + NVIDIA H200 + NIM path |
| [task-005](tasks/task-005.md) | READY | E | — | Add fail-closed real PDF + trusted metadata ingestion |
| [task-006](tasks/task-006.md) | BLOCKED | F | 001,002,003,004,005 | Integrate three governed NVIDIA execution boundaries |
| [task-007](tasks/task-007.md) | BLOCKED | G | 006 | Adapt payment approval flow to real Nemotron path |
| [task-008](tasks/task-008.md) | BLOCKED | H | 006,007 | Build contest evidence pack and adversarial/misroute proof suite |
| [task-009](tasks/task-009.md) | BLOCKED | I | 008 | Build `scripts/gtc-demo` and 60-second deterministic recording flow |
| [task-010](tasks/task-010.md) | BLOCKED | J | 009 | Final scoring review, freeze, README/submission assets |

## Critical path

```text
001 ─┐
002 ─┤
003 ─┤
004 ─┼─> 006 -> 007 -> 008 -> 009 -> 010
005 ─┘
```

Tasks 001–005 are intentionally parallel. Do not serialize them unless resource contention requires it.

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
