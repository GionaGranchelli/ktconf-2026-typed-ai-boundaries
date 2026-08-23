# KTConf 2026 — Typed AI Boundaries

*"The model may be nondeterministic. The boundaries around it should not be."*

An ordinary Spring Boot application demonstrating TramAI: one process, one
port, one governed runtime, two model routes — and a security boundary that
holds even when the application's own routing is wrong.

## The demo in one paragraph

The application receives an invoice with an **explicit classification**.
An exhaustive `when` in `InvoiceService` chooses the normal route:
PUBLIC/INTERNAL → cloud model, CONFIDENTIAL/RESTRICTED → local model.
TramAI then **independently enforces** whether that route is allowed for the
classification — and this repo proves it with a deliberately misrouted
request that TramAI must stop before any provider is invoked (cloud
invocation delta = 0).

> Classification is supplied. Routing chooses. Policy enforces.
>
> The application chooses a route. TramAI decides whether that route is allowed.

Note: the request's classification represents a **trusted upstream
governance fact** in this demo — the caller is expected to be a trusted
component that already decided the classification. TramAI enforces what the
classification *implies*; it is not a substitute for an external
classification/DLP step.

## Understand the integration in four files

| # | File | What it shows |
|---|---|---|
| 1 | `app/src/main/resources/application.yml` | all policy configuration: allowed models, providers, trust zones, tools, permissions |
| 2 | `app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt` | the typed `@AiService` boundary — `analyzeLocal` / `analyzeCloud` |
| 3 | `app/src/main/kotlin/dev/giona/ktconf/application/InvoiceService.kt` | routing: exhaustive `when` — PUBLIC/INTERNAL → cloud, CONFIDENTIAL/RESTRICTED → local |
| 4 | `app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt` | tool = authority: permission, risk, approval mode on the tool itself |

Everything else is ordinary Spring infrastructure (providers, controllers,
a small in-memory approval registry for the HTTP lifecycle).

## One app, two routes

```
ONE SPRING APP (one process, one port 8080)
          │
   explicit classification
          │
   application routing
     /          \
    /            \
local model    cloud model
 LOCAL        GLOBAL_CLOUD
    \            /
     \          /
    TramAI policy
          │
       provider
          │
  typed structured result
```

- `local-invoice-model` → `local-provider` → trust zone **LOCAL**
- `cloud-invoice-model` → `cloud-provider` → trust zone **GLOBAL_CLOUD**

Both live in the same Spring context and the same `SovereignTramaiRuntime`.
The sovereign starter (`tramai-spring-boot-starter-sovereign`) auto-configures
the runtime, the model registry, approval/continuation/audit stores and the
approval gate; it collects the `ModelProvider` beans and the
`SchedulePaymentTool` bean from the application context. The conference
application constructs **zero** TramAI infrastructure.

TramAI 0.6.x **validates** whether the selected route is allowed. It does
**not** automatically choose LOCAL for RESTRICTED input — policy-aware
provider selection is future (0.7) roadmap, deliberately not implemented here.

## Quick start

```bash
git clone --recursive https://github.com/GionaGranchelli/ktconf-2026-typed-ai-boundaries
cd ktconf-2026-typed-ai-boundaries
./scripts/preflight     # pin check + full deterministic suite + bootJar
./scripts/stage-up      # ONE app on :8080
```

Then, on the stage:

```bash
./scripts/demo typed            # PUBLIC  KTCONF-001   → cloud route  → 200 typed
./scripts/demo restricted       # RESTRICTED KTCONF-001 → local route → 200 typed
./scripts/demo restricted-cloud # RESTRICTED forced cloud → 403, delta 0
./scripts/demo invalid          # PUBLIC  KTCONF-INVALID → 422, no side effects
./scripts/demo payment          # RESTRICTED KTCONF-PAY → 202, awaiting approval
./scripts/demo approve <id>     # resume → payment executed exactly once
./scripts/demo approve <id>     # again → 409, payment still 1
./scripts/demo deny <id>        # deny → no payment, continuation refused
./scripts/demo evidence <id>    # 4 ordered audit events, chain valid
./scripts/demo stats            # cloudInvocationCount / paymentExecutionCount
```

Key line for the room: *"The HTTP request is finished. The workflow isn't."*
Then: *"And suddenly this doesn't look like an AI problem anymore. It looks
like distributed systems."*

## What is real vs simulated

- **Real TramAI behavior**: structured-output validation, classification-aware
  provider policy enforcement (denial BEFORE invocation), tool governance, approval
  suspension/continuation, hash-chained audit evidence.
- **Deterministic simulation**: the model responses (input-driven scripted
  providers) and the payment side effect (in-memory ledger). This is the
  conference stage: it cannot fail because of Wi-Fi, Tailscale, DeepSeek or
  model behavior.
- **Optional real providers** (off-stage, `./scripts/preflight-real`): the
  SAME identities become real OpenAI-compatible endpoints when configured:
  - `LOCAL` → Qwen3.8-27B-UD-Q6_K on the z840, reached over Tailscale
  - `CLOUD` → DeepSeek V4 Flash (`https://api.deepseek.com`)
  The governance configuration does NOT change — only the provider
  implementations behind those identities. Trust zones are operator
  assertions, never URLs. `preflight`/`stage-up` unset BOTH provider env
  families, so the deterministic oracle never silently calls a real model.

The `invalid` proof runs through the SAME application and SAME runtime:
nothing about the application changed, only the model response.

## History & fallback

- `ktconf-2026-demo-v4` — the ONE-app Spring conference freeze (this
  architecture; will be tagged after merge).
- `ktconf-2026-demo-v3` — the old four-profile Spring implementation
  (fallback only; do not rehearse with it).
- `ktconf-2026-demo-v2` — the frozen CLI implementation (historical).

## More

- [docs/](docs/README.md) — full documentation index, including a
  zero-context TramAI walkthrough (`docs/TRAMAI-PRIMER.md`).
