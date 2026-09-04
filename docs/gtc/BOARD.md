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
| [task-006](tasks/task-006.md) | DONE | F | 001,002,003,004,005 | Integrate three governed execution boundaries |
| [task-007](tasks/task-007.md) | DONE | G | 006 | Govern payment on the LOCAL NVIDIA execution boundary |
| [task-008](tasks/task-008.md) | DONE | H | 006,007 | Build contest evidence pack and adversarial/misroute proof suite |
| [task-009](tasks/task-009.md) | READY | I | 008 | Build `scripts/gtc-demo` and 60-second deterministic recording flow |
| [task-010](tasks/task-010.md) | BLOCKED | J | 009 | Final scoring review, freeze, README/submission assets |
| [task-012](tasks/task-012.md) | DONE | K | 005 | Preserve TramAI classification provenance and emit native sovereign evidence |
| [task-011](tasks/task-011.md) | REVIEW | UI | 005–008 | GTC governance console for the real backend evidence |
| [task-013](tasks/task-013.md) | REVIEW | UI | 006–011 | Backend-owned uploaded-document workflow history |
| [task-015](tasks/task-015.md) | DONE | Post-freeze | 007,013 | Safe reissue of expired approvals without reusing continuations |
| [task-016](tasks/task-016.md) | DONE | Post-freeze | 004,006 | Use Scaleway serverless Generative APIs for the temporary EU provider |
| [task-017](tasks/task-017.md) | BLOCKED | Post-freeze | 010 | Retire redundant invoice endpoints and AI operations |

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
- task-016 is `DONE`: the active EU deployment now uses Scaleway serverless
  Generative APIs with `mistral-medium-3.5-128b`. The live smoke validated
  `/v1/models` HTTP 200 and model advertisement, direct chat HTTP 200, typed
  application HTTP 200 with `selectedRoute=EU_CLOUD` and allowed invocation
  delta `1`, plus forced restricted-EU HTTP 403 with
  `classification-routing-blocked` and invocation delta `0`. The former
  dedicated Scaleway evidence remains historical.
- High-risk documents on every governed boundary now use the same TramAI
  `schedule-payment` operation. `ApprovalFlowTest` proves the confidential EU
  PDF and a public GLOBAL request suspend with HTTP 202, keep payment count at
  `0`, then resume after approval and execute exactly once.
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
real route proofs exist, and the combined real PDF run passed. Task-007 is
`DONE`: deterministic payment integration is complete and the real local
Qwen-on-RTX payment smoke passed; its sanitized evidence is recorded in
[`evidence/local-nvidia-payment-smoke.md`](evidence/local-nvidia-payment-smoke.md).
Task-008 is `DONE`: deterministic and combined real-provider evidence are
recorded; task-009 is now `READY` and task-010 remains blocked behind it.

The combined live command is `scripts/gtc-real-boundaries-smoke`; it requires
all three configured provider families and uploads the three synthetic PDFs in
one Spring process. The command and payment smoke completed successfully; their
sanitized evidence is recorded under `docs/gtc/evidence/`.

Live execution checkpoint: `./scripts/gtc-evidence` passed. The combined real
runner passed with GLOBAL hosted Nemotron, EU Scaleway/Mistral, and LOCAL
Qwen-on-RTX, including all denial counter deltas. The local Qwen payment
runner passed suspension, approval, duplicate rejection, and audit validation.
Sanitized artifacts are recorded under `docs/gtc/evidence/`; 006, 007, and 008
are closed and 009 is ready.

Latest closure checkpoint: the assessment/tool prompt explicitly defines the
EUR 5,000 risk/action rule and the post-`schedule-payment` state transition.
The canonical payment PDF denial test proves denial leaves payment count at
`0` and later continuation returns `409`. Full deterministic tests and
rehearsal remain green. The real Qwen-on-RTX payment proof passed and is
documented; no real Nemotron payment or combined real-PDF claim is made.
An audit also removed stale active-path defaults: restricted-local deterministic
output now matches its €42 PDF, and local-NVIDIA payment defaults to Qwen.
Typed model output is also reconciled against the trusted invoice amount and
the €5,000 approval threshold before it is returned to the dashboard.

Latest cleanup checkpoint: the abandoned nested frontend scaffold and
unreachable frontend state were removed; controller outcome mapping is shared;
PDF denial proofs now use the generic
`/invoices/analyze-pdf?forceRoute=...` entrypoint; trusted PDF parsing exposes
only its explicit metadata and extraction phases; and replay evidence accepts
only the documented HTTP 409 conflict. Focused routing/PDF tests pass under
the deterministic provider scrub, and no governance or provider policy was
changed.

Task-011 is now present on this branch as a Vue/Vite governance console. It
uses the active Scaleway/Mistral EU identity and `euScalewayInvocationCount`,
keeps LOCAL Qwen and GLOBAL Nemotron labels truthful, and proxies the real
Spring endpoints from port 3001 to port 8080. `npm run build` passes on Node
22.23.1 / npm 10.9.8. Browser visual smoke against a running Spring app is
still pending; no provider or governance semantics are implemented in the UI.
The overview is now the landing screen and presents the problem, guarantees,
architecture, NVIDIA relationship, and live-proof CTAs as one recording-ready
story. Presentation mode hides sidebar/debug chrome, and the proof CTAs open
the existing live workflow with a placement or action-governance hint.

Task-013 adds backend-owned demo-session history for uploaded PDFs at
`/governance/documents` and a Document History dashboard page. Details include
trusted metadata, selected route, invoice context, typed assessment or
approval, payment status, and readable tool/notification/audit events. History
is intentionally in-memory and resets when the application restarts. It is
reachable from the persistent dashboard top bar as `Document history`, and
the Overview page also exposes `View processed documents`. Each record now
also shows upload and policy outcome events, including `auto-schedule-payment`
when the trusted low-risk rule allows automatic payment. Denied forced-route PDF attempts are also retained
with status `DENIED`, their TramAI reason code, and a provider-not-invoked
timeline event.

Latest payment consistency checkpoint: the trusted €5,000 rule now selects a
separate `auto-schedule-payment` tool for LOW-risk invoices (`LOW`/`AUTO`, full
audit, invoice-scoped idempotency) and keeps `schedule-payment` for HIGH-risk
invoices (`HIGH`/`HUMAN_REQUIRED`). The full deterministic application suite
passes after this change; the frontend build also passes.

Containerized demo packaging is also available through `Dockerfile` and
`docker-compose.yml`: the frontend is embedded into the Spring Boot image,
the API is exposed on `:8080`, and the console is served at `/gtc/`. Compose
passes provider configuration only from the operator environment and maps
`host.docker.internal` for an optional host llama.cpp endpoint. Deterministic
container startup was verified with health HTTP 200 and console HTTP 200.

Task-012 is a DONE TramAI-native enhancement task. It covers
`RULE_BASED` PDF provenance and sovereign evidence-pack exposure, with local
artifact verification and telemetry de-duplication bounded as optional work.
It is planned separately from the current task-008 evidence review. Its
implementation is complete; deterministic verification passed.
The native pack is exposed at `/governance/sovereign-evidence` and documented
in [`evidence/tramai-sovereign-evidence.md`](evidence/tramai-sovereign-evidence.md).
Local Nemotron artifact verification remains explicitly unclaimed because the
pinned Spring composition does not configure an artifact manifest/verifier.

Task-015 is `DONE`: expired PDF-backed approvals can be reissued through
`POST /approvals/{approvalId}/reissue` only after TramAI accepts the old
approval's `Timeout` transition. The old approval is recorded as `EXPIRED` and
cannot resume; a fresh governed analysis creates a new continuation, approval,
fake approval email, and linked history record with readable expiry/reissue
events. Active, terminal, unknown, and legacy workflow approvals fail safely.
The focused expiry test, full deterministic app suite, and frontend build pass.
This recovery remains in-memory and operator-triggered for demo scope.

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
5. A real local model on NVIDIA RTX can propose the governed payment action but cannot execute it without TramAI's human-approval gate.
6. Approval resumes exactly once in demo scope; duplicate approval is rejected.
7. Hash-chained audit evidence verifies.
8. The deterministic offline path remains green.
9. No secrets appear in source, logs, screenshots, video, evidence, or Git history.
10. A reviewer can understand the project and reproduce the primary proof from the repository without reading chat history.
