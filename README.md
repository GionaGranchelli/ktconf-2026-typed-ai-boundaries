> [!IMPORTANT]
> **NVIDIA GTC Golden Ticket workstream:** this branch is being adapted into **The Model Is Not the Authority** — a real-document demo with LOCAL NVIDIA, temporary Scaleway/Mistral EU, and GLOBAL NVIDIA execution boundaries. Mistral is not NVIDIA/Nemotron/NIM. Start with [`GTC-2026-SUBMISSION.md`](GTC-2026-SUBMISSION.md) and [`docs/gtc/ROADMAP.md`](docs/gtc/ROADMAP.md). The KTConf baseline below remains the deterministic foundation and must not be weakened.

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
| 2 | `app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt` | the typed `@AiService` boundary — governed local, cloud, and tool-free assessment operations |
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
application constructs **zero low-level TramAI stores or coordinators**. One
explicit composition bean reuses the starter-provided infrastructure to attach
the OpenTelemetry observer, because the starter does not yet expose that hook.

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
./scripts/demo workflow-payment # explicit workflow → AI rationale + approval email + 202
./scripts/demo approve <id>     # resume → payment executed exactly once
./scripts/demo approve <id>     # again → 409, payment still 1
./scripts/demo deny <id>        # deny → no payment, continuation refused
./scripts/demo evidence <id>    # 4 ordered audit events, chain valid
./scripts/demo stats            # cloudInvocationCount / paymentExecutionCount
```

## Isolated workflow-demo flow

The main invoice and approval flow is unchanged. This endpoint accepts the
same request and uses the same typed AI operations as `/invoices/analyze`, but
expresses the work as an explicit state machine:

```bash
curl -sS -X POST http://localhost:8080/workflow-demo/analyze \
  -H 'Content-Type: application/json' \
  -d '{"classification":"PUBLIC","invoice":{"invoiceId":"KTCONF-001","supplierName":"KTConf Catering BV","amountCents":42830,"currency":"EUR","description":"Conference catering services"}}'
```

Its bounded steps are:

```text
classify → route → assess → amount-above-€5k approval gate → notify-approver → finalize
```

The AI produces a tool-free typed assessment first, so a suspended response can
show the real model rationale and a TramAI-validated confidence in the inclusive
range `0.0..1.0`. A trusted application rule—not the model—then requires human
approval when `amountCents > 500_000`. The next deterministic workflow step
records a fake approval email containing the real approval ID. Payment is
scheduled exactly once only after approval. With `stage-observe-up`,
the run creates an `invoice-approval-demo` workflow trace in Jaeger.

### Local trace rehearsal (optional)

For a local browser view of TramAI's per-attempt traces, run:

```bash
./scripts/stage-observe-up
./scripts/demo typed
```

Open <http://localhost:16686>, select service `ktconf-demo`, and inspect the
`invoice.model.call` span and its nested `ai.analyzeCloud` attempt. The parent
records classification, selected route, logical model, provider, and trust zone;
a denied route adds a `governance.policy.denied` event without exposing invoice
content. The stack is loopback-only: the app exports OTLP/HTTP to Jaeger at
`localhost:4318`. Stop both with `./scripts/stage-observe-down`.

The observability rehearsal runs both the app and Jaeger in Docker. Watch the
application's structured logs with:

```bash
docker compose -f docker-compose.observability.yml logs -f app
```

To use the real DeepSeek provider in this Dockerized rehearsal, export its
normal host variable before starting the stack:

```bash
export DEEPSEEK_API_KEY="..."
./scripts/stage-observe-up
```

The script maps it to the application's `KTCONF_DEMO_CLOUD_API_KEY` at container
runtime. An explicitly supplied `KTCONF_DEMO_CLOUD_API_KEY` takes precedence.
The credential is not passed to the Docker build or stored in the image.

Real local models may spend substantially longer generating than the scripted
stage provider. TramAI limits each provider attempt to 90 seconds, while Spring's
outer asynchronous workflow request allows 180 seconds. Override the latter with
`KTCONF_HTTP_ASYNC_TIMEOUT` (for example, `240s`) if needed.

The normal `stage-up` path remains offline and does not export telemetry.

### Run the API and governance console with Docker

The repository also includes a single-container Compose deployment. It builds
the Vue console into the Spring Boot jar, serves the API on port `8080`, and
serves the console at <http://localhost:8080/gtc/>.

```bash
docker compose up --build
```

Provider credentials are opt-in environment variables and are never stored in
the image. Without them, the application uses its deterministic providers. To
connect a host llama.cpp server from the container, set for example:

```bash
export KTCONF_GTC_LOCAL_NVIDIA_BASE_URL=http://host.docker.internal:1234/v1
export KTCONF_GTC_LOCAL_NVIDIA_MODEL=qwen/qwen3.8-27b
```

Set the existing global NVIDIA and Scaleway variables in the shell when live
cloud inference is intended. `host.docker.internal` is configured in Compose
for Linux as well as Docker Desktop.

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
