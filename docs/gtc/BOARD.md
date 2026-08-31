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
| [task-002](tasks/task-002.md) | DONE | B | — | Prove GLOBAL Build.NVIDIA.com Nemotron inference |
| [task-003](tasks/task-003.md) | DONE | C | — | Prove LOCAL RTX + Nemotron inference |
| [task-004](tasks/task-004.md) | DONE | D | — | Prove EU_CLOUD managed inference via Scaleway Generative APIs |
| [task-005](tasks/task-005.md) | DONE | E | — | Add fail-closed real PDF + trusted metadata ingestion |
| [task-006](tasks/task-006.md) | REVIEW | F | 001,002,003,004,005 | Integrate three governed execution boundaries |
| [task-007](tasks/task-007.md) | REVIEW | G | 006 | Adapt payment approval flow to real Nemotron path |
| [task-008](tasks/task-008.md) | REVIEW | H | 006,007 | Build contest evidence pack and adversarial/misroute proof suite |
| [task-009](tasks/task-009.md) | BLOCKED | I | 008 | Build `scripts/gtc-demo` and 60-second deterministic recording flow |
| [task-010](tasks/task-010.md) | BLOCKED | J | 009 | Final scoring review, freeze, README/submission assets |
| [task-012](tasks/task-012.md) | READY | K | 005 | Preserve TramAI classification provenance and emit native sovereign evidence |

## Evidence checkpoint

- task-003 is `DONE`: local smoke passed with RTX 3060 / driver 580.173.02,
  llama.cpp 9986, Nemotron `nvidia/nemotron-3-nano-4b`, direct HTTP 200, typed
  application HTTP 200, `selectedRoute=LOCAL_NVIDIA`, and invocation count 1.
- task-002 is `DONE`: fresh-shell NVIDIA smoke validated the hosted model
  catalog, direct HTTP 200, typed application HTTP 200 with
  `selectedRoute=GLOBAL_CLOUD`, invocation count 1, and restricted denial
  before provider invocation with counter delta `0`. Sanitized evidence is in
  [`evidence/global-nvidia-smoke.md`](evidence/global-nvidia-smoke.md).
- task-004 is `DONE`: Nebius H200/H100 attempts remain historical evidence
  of `code=13` workload failures and `code=8 NotEnoughResources` scheduling
  failures; no Nebius inference is claimed. The active EU implementation uses
  `eu-scaleway-provider` and `eu-scaleway-invoice-model` in `EU_CLOUD`,
  reusing the OpenAI-compatible, model-alias, and counting-provider
  composition. The real Scaleway smoke passed model catalog validation,
  direct chat, typed application HTTP 200 with `selectedRoute=EU_CLOUD`,
  allowed invocation delta `1`, and forced restricted-EU HTTP 403 with
  invocation delta `0`.
- The deterministic stage scripts now clear every contest real-provider
  family (`LOCAL_NVIDIA`, `EU_SCALEWAY`, `GLOBAL_NVIDIA`) plus generic
  `SCW_*` fallbacks. `ScriptSanitizationTest` covers those variables, so an
  operator's exported Scaleway environment cannot alter the offline stage.
- Sanitized real-run evidence is recorded in
  [`evidence/scaleway-smoke.md`](evidence/scaleway-smoke.md); it contains no
  endpoint UUID, account identifier, URL, token, or key.
- task-005 is `DONE`: PDFBox ingestion, documented metadata contract, three
  synthetic fixtures, separate metadata/content phases, and fail-closed
  multipart counter tests are complete. The full app suite passed; malformed
  multipart rejection returned HTTP 400 with all provider counters unchanged.

Task-006 is `REVIEW`: trusted PDF residency now selects the governed
`LOCAL_NVIDIA`, `EU_CLOUD`, or `GLOBAL_CLOUD` route, while TramAI authorizes
the selected operation. The PDF metadata contract is fail-closed to the
classification-aligned residency combinations. Deterministic tests prove one
allowed invocation per boundary; the confidential-EU forced-global PDF test
proves HTTP 403 and global counter delta `0`, followed by EU success. Individual
real route proofs exist, but a combined real PDF run remains pending. Task-007 is
in `REVIEW`: deterministic payment integration is complete, but its real
Nemotron payment smoke is pending local endpoint availability. Task-008 is now
the owner of the remaining real-provider evidence runs; tasks 009–010 remain
blocked behind task-008.

The combined live command is `scripts/gtc-real-boundaries-smoke`; it requires
all three configured provider families and uploads the three synthetic PDFs in
one Spring process. No live result is recorded until that command and the
payment smoke complete successfully against the final evidence revision.

Live execution checkpoint: `./scripts/gtc-evidence` passed, but the combined
runner stopped before startup because the sourced shell lacked the local/global
model configuration and Scaleway API key. The local payment runner then failed
to connect to `127.0.0.1:1234`. Therefore no new live artifact or closure status
was recorded; 006, 007, and 008 remain `REVIEW`, and 009 remains `BLOCKED`.

Latest closure checkpoint: the assessment/tool prompt explicitly defines the
EUR 5,000 risk/action rule and the post-`schedule-payment` state transition.
The canonical payment PDF denial test proves denial leaves payment count at
`0` and later continuation returns `409`. Full deterministic tests and
rehearsal remain green; no real Nemotron payment or combined real-PDF claim is
made.

Task-011 is maintained on the separate `task/011-gtc-governance-console`
branch and was not modified here. Before merging it, update the EU card and
counter to the active Scaleway/Mistral implementation: `Scaleway Europe`,
`Generative APIs`, `Mistral Small 24B`, and `euScalewayInvocationCount` (or a
generic EU-provider label). The UI must not claim Nebius, H200, NVIDIA NIM, or
Nemotron for the temporary EU route.

Task-012 is a new READY enhancement task. It covers TramAI-native
`RULE_BASED` PDF provenance and sovereign evidence-pack exposure, with local
artifact verification and telemetry de-duplication bounded as optional work.
It is planned separately from the current task-008 evidence review and has
not been started.

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
2. The same application exposes three governed execution boundaries: NVIDIA-backed `LOCAL` and `GLOBAL_CLOUD`, plus temporary Scaleway/Mistral `EU_CLOUD`.
3. At least one real successful inference is proven for each boundary.
4. A disallowed route is denied before provider invocation and the corresponding provider counter delta is exactly `0`.
5. Nemotron can propose the governed payment action but cannot execute it without TramAI's human-approval gate.
6. Approval resumes exactly once in demo scope; duplicate approval is rejected.
7. Hash-chained audit evidence verifies.
8. The deterministic offline path remains green.
9. No secrets appear in source, logs, screenshots, video, evidence, or Git history.
10. A reviewer can understand the project and reproduce the primary proof from the repository without reading chat history.
