# KTConf 2026 — Typed AI Boundaries

**«The model may be nondeterministic. The boundaries around it should not be.»**

Standalone conference demo repository for the KTConf 2026 talk
*Typed AI Boundaries: A Kotlin Approach to Production AI Systems*.

This repository is **not** part of the TramAI source tree. It is an external
Kotlin/JVM application that consumes [TramAI](https://github.com/GionaGranchelli/tramAI)
as an immutable, pinned library dependency — exactly the way a backend
engineer integrating TramAI into a real application would experience it.

## What the demo proves

Every scenario runs against **real TramAI behavior** — the only fake is a
deterministic scripted model provider.

| Scenario | Command | Proves |
|---|---|---|
| Typed boundary | `./scripts/demo typed` | valid model output → typed `InvoiceAssessment`, no manual JSON mapping |
| Broken model | `./scripts/demo invalid` | invalid structured output rejected by the engine, zero side effects |
| Restricted data | `./scripts/demo restricted` | RESTRICTED input denied on a cloud provider *before invocation*, LOCAL allowed |
| Approval | `./scripts/demo approval` | HIGH-risk tool suspends, human approves/denies, exactly-once execution |
| Evidence | `./scripts/demo evidence` | real hash-chained audit records, verified chain, evidence pack |

Run everything in one pass:

```bash
./scripts/rehearse          # reset + all scenarios, non-interactive
```

## Quick start (clean machine)

Requirements: JDK 21, Git, network for the first preparation only.

```bash
git clone --recursive https://github.com/GionaGranchelli/ktconf-2026-typed-ai-boundaries
cd ktconf-2026-typed-ai-boundaries
./scripts/preflight         # verifies pinned TramAI revision + runs the full test suite
./scripts/demo all          # stage: deterministic demo, offline-capable
```

After `preflight`, the demo runs **without** internet, GitHub, Maven Central,
cloud providers, Tailscale, Docker, or a database — a stage laptop can go
into airplane mode.

## Pinned TramAI revision

| | |
|---|---|
| Commit | `9debb0f2f17bfcb117ada757b946d4e8263c2106` |
| Artifact version | `0.5.0` (self-declared by the pinned build) |
| Consumed via | Git submodule `vendor/tramai` + Gradle composite build |

The submodule is **read-only**: if the demo exposes a defect or missing
capability in TramAI, the conference repository does not patch around it —
the defect is reported as a separate upstream TramAI task.

See [docs/TRAMAI-INTEGRATION.md](docs/TRAMAI-INTEGRATION.md) for the
dependency strategy, upgrade procedure, and offline story.

## Repository layout

```
app/                 conference application (Kotlin/JVM, JDK 21, Gradle)
  src/main/kotlin/dev/giona/ktconf/
    domain/          fictional invoice domain + demo fixtures
    ai/              @AiService boundary + deterministic providers
    runtime/         DemoRuntimeFactory (real SovereignTramai wiring)
    tools/           schedule-payment tool + exactly-once ledger
    scenarios/       the six demo scenarios
    presentation/    stage formatting
    cli/             stable stage API
  src/test/kotlin/   semantic tests (one per proven behavior)
docs/                architecture, demo script, integration, claims
scripts/             demo | preflight | rehearse
vendor/tramai/       pinned TramAI submodule (read-only)
```

## Core rule

> If the demo exposes a defect or missing capability in TramAI, do not patch
> TramAI from this repository. Stop and report a separate upstream TramAI task.

See [docs/CLAIMS-BOUNDARY.md](docs/CLAIMS-BOUNDARY.md) for exactly what this
demo does and does not claim.
