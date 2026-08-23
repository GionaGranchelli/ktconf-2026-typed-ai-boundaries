# Architecture

## Three concerns, kept visibly separate

```
incoming request
      ↓
explicit classification      ← 1. CLASSIFICATION  (supplied, never inferred)
      ↓
application route selection  ← 2. ROUTING  (tiny when: RESTRICTED → local)
      ↓
TramAI policy check          ← 3. POLICY ENFORCEMENT  (independent backstop)
      ↓
provider executes
      ↓
typed structured result
```

> Classification is supplied. Routing chooses. Policy enforces.
> The application chooses a route. TramAI decides whether that route is allowed.

The routing `when` stays visible in the application on purpose: it makes the
independent enforcement backstop demonstrable. If the application misroutes
RESTRICTED data to the cloud, TramAI denies it before the provider is
invoked — the `/invoices/boundary/restricted-cloud` endpoint exists to prove
exactly that.

## One runtime, two routes

```
ONE Spring Boot application (one process, one port 8080)

tramai-spring-boot-starter-sovereign  (auto-configuration)
  ├── SovereignTramaiRuntime (exactly ONE bean)
  ├── ModelRegistry (from application.yml models)
  ├── InMemoryAuditStore / ApprovalStore / ContinuationStore
  ├── DefaultApprovalGateCoordinator + digesters + token generator
  └── collects from the Spring context:
        ModelProvider beans    → local-provider, cloud-provider
        TramaiTool beans       → SchedulePaymentTool (upstream tramAI #268)

application.yml:
  models:            local-invoice-model → local-provider
                     cloud-invoice-model → cloud-provider
  provider-zones:    local-provider: LOCAL
                     cloud-provider: GLOBAL_CLOUD
```

The conference application constructs **zero** sovereign infrastructure:
no builder, no stores, no coordinators, no digesters. An architecture-guard
test fails if that ever regresses.

## Application layers (this repo)

```
domain/          InvoiceDocument, InvoiceAssessment, AnalyzeInvoiceRequest,
                 DataClassification (TramAI enum, supplied by the caller)
ai/              InvoiceAnalysisService (@AiService, two @Operation routes)
application/     InvoiceService (routing when + suspension mapping)
                 PendingApprovalRegistry (HTTP approvalId → server-side
                   challenge token; token NEVER leaves the server)
                 ApprovalService (approve/deny via real resume command)
                 EvidenceService (per-workflow evidence pack)
api/             InvoiceController (+ demo-only /boundary/restricted-cloud)
                 ApprovalController, GovernanceStatsController (+ /healthz),
                 ApiExceptionAdvice (readable error mapping)
governance/      ProvidersConfiguration (local + cloud deterministic
                   providers; optional real local provider via env)
demo/            DeterministicProvider (input-driven script)
                 DemoResponses
payments/        SchedulePaymentTool (tool = authority: permission, risk,
                   approval mode, side effect, egress, audit)
                 InMemoryPaymentLedger (exactly-once via idempotency key)
```

The important names, clickable:

| File | Role |
|---|---|
| [InvoiceAnalysisService.kt](../app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt) | the `@AiService` typed boundary — two governed routes |
| [InvoiceService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceService.kt) | classification → routing → suspension mapping |
| [ProvidersConfiguration.kt](../app/src/main/kotlin/dev/giona/ktconf/governance/ProvidersConfiguration.kt) | the ONLY infrastructure config: local + cloud provider beans |
| [SchedulePaymentTool.kt](../app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt) | tool = authority: permission, risk, approval mode |
| [ApprovalService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/ApprovalService.kt) | approve/deny via the real resume command |
| [EvidenceService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/EvidenceService.kt) | per-workflow evidence pack, chain verified |

## The typed boundary

Two layers, deliberately distinct:

- **Application port:** `InvoiceService` routes through the runtime-created
  `InvoiceAnalysisService` proxy. Ordinary Spring.
- **The actual TramAI typed AI boundary:** the `@AiService`
  `InvoiceAnalysisService` contract — `ClassifiedDocument<InvoiceDocument>`
  → `InvoiceAssessment`, with one `@Operation` per model route. Application
  code sees a compile-time-typed surface; TramAI validates model output at
  runtime before allowing it to cross that boundary.

```kotlin
val assessment: InvoiceAssessment = ai.analyzeLocal(document)
```

Business code does **not** parse model JSON, select provider SDKs, perform
prompt extraction, evaluate security rules, or implement approval state
machines. All of that happens inside TramAI, behind the typed boundary.

New to TramAI? [TRAMAI-PRIMER.md](TRAMAI-PRIMER.md) explains the concepts
behind these files in ten minutes.

## HTTP semantics

| Status | Meaning |
|---|---|
| 200 | successful typed result / successful approval action |
| 202 | workflow suspended awaiting approval (the HTTP request finished) |
| 403 | TramAI policy denies the route before provider invocation |
| 409 | invalid/consumed/denied approval continuation state |
| 422 | model output rejected at the structured boundary |
