# KTConf 2026 — Typed AI Boundaries

**«The model may be nondeterministic. The boundaries around it should not be.»**

Standalone conference demo repository for the KTConf 2026 talk
*Typed AI Boundaries: A Kotlin Approach to Production AI Systems*.

This repository is **not** part of the TramAI source tree. It is an external
Kotlin/Spring Boot application that consumes
[TramAI](https://github.com/GionaGranchelli/tramAI) as an immutable, pinned
library dependency — exactly the way a backend engineer integrating TramAI
into a real application would experience it.

The demo is an ordinary Kotlin backend. Watch what happens when AI is treated
as governed infrastructure rather than magic.

## What the demo proves

One application, one bootable jar, four runtime configurations. The same
`InvoiceApplicationService` runs behind every profile — only the model
infrastructure bean differs.

| Instance | Profile | Port | Proves |
|---|---|---|---|
| Demo | `demo` | `:8080` | valid model output → typed `InvoiceAssessment`; HIGH-risk tool suspends → human approve/deny; hash-chained evidence |
| Broken | `broken` | `:8081` | garbage model output rejected by the engine (422), zero side effects — the boundary holds, not the prompt |
| Cloud-routing | `cloud-routing` | `:8082` | RESTRICTED input denied on a `GLOBAL_CLOUD` provider *before invocation* (counter stays 0) |
| Real (optional) | `real` | `:8083` | a real LLM behind the same typed input/output contract, declared LOCAL by operator assertion |

Structured output, policy routing, tool governance, approval/continuation,
and audit/evidence are **real TramAI behavior**; only the model responses are
scripted (and the real profile replaces them with an actual LLM).

## Quick start (clean machine)

Requirements: JDK 21, Git, network for the first preparation only.

```bash
git clone --recursive https://github.com/GionaGranchelli/ktconf-2026-typed-ai-boundaries
cd ktconf-2026-typed-ai-boundaries
./scripts/preflight         # verifies pinned TramAI revision + runs the full test suite + builds the jar
./scripts/stage-up          # starts demo :8080, broken :8081, cloud-routing :8082 (waits for each)
./scripts/demo typed        # POST /invoices/analyze -> typed InvoiceAssessment, HTTP 200
```

After `preflight` and `stage-up`, the demo runs **without** internet, GitHub,
Maven Central, cloud providers, Tailscale, Docker, or a database — a stage
laptop can go into airplane mode. `scripts/stage-down` stops the instances.

### The stable stage API

`./scripts/demo <command>` curls the running instances (never restarts them):

```
./scripts/demo typed           # demo :8080, KTCONF-001         -> 200 typed
./scripts/demo real            # real :8083, KTCONF-001         -> 200 typed (when started)
./scripts/demo invalid         # broken :8081, KTCONF-001       -> 422 structured-output-rejected
./scripts/demo restricted      # cloud :8082, KTCONF-RESTRICTED -> 403 classification-routing-blocked
./scripts/demo payment         # demo :8080, KTCONF-PAY-001     -> 202 AWAITING_APPROVAL
./scripts/demo approve <id>    # POST /approvals/<id>/approve   -> 200, payment 0 -> 1
./scripts/demo deny <id>       # POST /approvals/<id>/deny      -> 200 DENIED, payment stays 0
./scripts/demo evidence <id>   # GET /approvals/<id>/evidence   -> 4-event chain, verified
./scripts/demo stats           # GET /governance/stats          -> cloud + payment counters
./scripts/demo healthz [port]  # GET /governance/healthz        -> {status, profile}
```

The approval token never leaves the server; the API only exposes
`approvalId` + `workflowRunId`. See [docs/DEMO-SCRIPT.md](docs/DEMO-SCRIPT.md)
for the conference narrative.

## Real-model path (optional, opt-in)

```bash
export KTCONF_DEMO_LOCAL_BASE_URL=http://localhost:11434/v1   # LOCAL/private endpoint you intentionally trust
export KTCONF_DEMO_LOCAL_MODEL=qwen3:8b                        # model name
./scripts/stage-up            # also starts real :8083
./scripts/demo real           # the real LLM behind the typed boundary
./scripts/preflight-real      # conference-morning check: endpoint -> model -> real-profile test must pass
```

The `real` profile is the only real-model path; every governance scenario
stays deterministic. The endpoint is declared LOCAL by operator assertion —
do not point it at a public cloud API. See
[docs/CLAIMS-BOUNDARY.md](docs/CLAIMS-BOUNDARY.md).

## Rehearsal gates

```bash
./scripts/rehearse          # full deterministic suite, fresh contexts
./scripts/stress-rehearse   # the conference gate: full oracle 20/20 per profile
```

`stress-rehearse` runs the complete deterministic oracle twenty times on
fresh application contexts per profile (demo: typed → deny → approve →
duplicate → evidence; broken: 422; cloud: 403 + zero invocations) and
verifies the result XMLs — `60 / 60 PASS`.

## Pinned TramAI revision

| | |
|---|---|
| Commit | `1ce840fac7a6319e6f1ab8f9a005f92cd2acd691` |
| Artifact version | `0.5.0` (self-declared by the pinned build) |
| Consumed via | Git submodule `vendor/tramai` + Gradle composite build |

The submodule is **read-only**: if the demo exposes a defect or missing
capability in TramAI, the conference repository does not patch around it —
the defect is reported as a separate upstream TramAI task.

See [docs/TRAMAI-INTEGRATION.md](docs/TRAMAI-INTEGRATION.md) for the
dependency strategy, upgrade procedure, and offline story.

## Repository layout

```
app/                 conference application (Kotlin/JVM, JDK 21, Spring Boot, Gradle)
  src/main/kotlin/dev/giona/ktconf/
    domain/          fictional invoice domain + demo fixtures
    ai/              @AiService boundaries (compile-time typed surface)
    application/     InvoiceApplicationService + InvoiceAnalyzer port,
                     PendingApprovalRegistry, ApprovalService, EvidenceService
    api/             REST controllers + error mapping (200/202/403/409/422)
    governance/      Spring profiles -> TramAI infrastructure beans
    demo/            scripted deterministic provider + responses
    payments/        schedule-payment tool + exactly-once ledger
  src/main/resources/ application.yml
  src/test/kotlin/   semantic tests (one per proven behavior) + 20/20 rehearsals
docs/                architecture, demo script, integration, claims
                     (start at docs/README.md — index + FAQ)
scripts/             demo | stage-up | stage-down | preflight |
                     preflight-real | rehearse | stress-rehearse
vendor/tramai/       pinned TramAI submodule (read-only)
```

## Core rule

> If the demo exposes a defect or missing capability in TramAI, do not patch
> TramAI from this repository. Stop and report a separate upstream TramAI task.

See [docs/CLAIMS-BOUNDARY.md](docs/CLAIMS-BOUNDARY.md) for exactly what this
demo does and does not claim.
