# Architecture

## Two systems

```
┌─────────────────────────────┐          ┌──────────────────────────────┐
│ TramAI repository           │          │ ktconf-2026-typed-ai-        │
│ (upstream library)          │          │ boundaries (this repo)       │
│                             │          │                              │
│ read-only, pinned commit    │◄─────────│ real library dependency      │
│ vendor/tramai (submodule)   │          │ composite build              │
└─────────────────────────────┘          │                              │
                                         │ ordinary Kotlin/Spring Boot  │
                                         │ application                   │
                                         │  api -> application -> ai    │
                                         │  -> TramAI (governed infra)  │
                                         └──────────────────────────────┘
```

No conference code contaminates TramAI, and no presentation-specific hacks
become library APIs.

## One application, four runtime configurations

One bootable jar (`ktconf-demo.jar`). Spring profiles select the model
infrastructure bean; `InvoiceApplicationService` is identical in every
profile and contains no profile branching.

```
HTTP
  ↓
Controller (api/)               200 typed | 202 suspended | 403 policy
                                | 409 approval conflict | 422 structured failure
  ↓
InvoiceApplicationService        classifies RESTRICTED/DECLARED,
  ↓                              catches ApprovalSuspendedException
InvoiceAnalyzer (port)           one adapter per profile
  ↓
TramAI SovereignTramai           real library: provider routing,
  ↓                              policy, tool governance, approval,
provider / policy / tool /       continuation, audit/evidence
approval / audit
```

## Application layers (this repo)

```
domain/          InvoiceDocument, InvoiceAssessment, DemoInvoices
                 ↑ typed input / typed result — the audience sees THIS
ai/              InvoiceAnalysisService, RealInvoiceAnalysisService
                 (@AiService boundaries, compile-time typed surface)
application/     InvoiceApplicationService (no profile branching)
                 InvoiceAnalyzer (application port)
                 PendingApprovalRegistry (server-side approval state;
                   the challenge token NEVER leaves the server)
                 ApprovalService (approve/deny via resume command)
                 EvidenceService (per-workflow evidence pack)
api/             InvoiceController, ApprovalController,
                 GovernanceStatsController (+ /healthz with profile identity),
                 ApiExceptionAdvice (readable error mapping)
governance/      per-profile @Configuration: demo | broken | cloud-routing | real
                 TramaiConfiguration: shared singleton stores + gate coordinator
demo/            ScriptedProvider (request-driven deterministic model)
                 DemoResponses
payments/        SchedulePaymentTool (security metadata on the tool)
                 InMemoryPaymentLedger (exactly-once via idempotency key)
```

The important names, clickable:

| File | Role |
|---|---|
| [InvoiceAnalysisService.kt](../app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt) | the `@AiService` typed boundary — typed in, typed out |
| [InvoiceApplicationService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceApplicationService.kt) | classifies the document, catches only approval suspension |
| [InvoiceAnalyzer.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceAnalyzer.kt) | application port; one adapter per profile |
| [TramaiConfiguration.kt](../app/src/main/kotlin/dev/giona/ktconf/governance/TramaiConfiguration.kt) | shared singleton stores, digesters, gate coordinator |
| [DemoConfiguration.kt](../app/src/main/kotlin/dev/giona/ktconf/governance/DemoConfiguration.kt) | one managed `SovereignTramaiRuntime` per instance |
| [CloudRoutingConfiguration.kt](../app/src/main/kotlin/dev/giona/ktconf/governance/CloudRoutingConfiguration.kt) | the RESTRICTED + GLOBAL_CLOUD denial invariant |
| [SchedulePaymentTool.kt](../app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt) | tool = authority: permission, risk, approval mode |
| [ApprovalService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/ApprovalService.kt) | approve/deny via the real resume command |
| [EvidenceService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/EvidenceService.kt) | per-workflow evidence pack, chain verified |

New to TramAI? [TRAMAI-PRIMER.md](TRAMAI-PRIMER.md) explains the concepts
behind these files in ten minutes.

## The boundaries the audience should remember

Two layers, deliberately distinct:

- **Application port:** `InvoiceAnalyzer` — the adapter through which the
  application service talks to AI. Good architecture, but a Spring detail.
- **The actual TramAI typed AI boundary:** the `@AiService`
  `InvoiceAnalysisService` contract — `ClassifiedDocument<InvoiceDocument>`
  → `InvoiceAssessment`. This is where the typed-boundary argument becomes
  concrete: application code sees a compile-time-typed surface; TramAI
  validates model output at runtime before allowing it to cross that
  boundary. The conference audience should remember THIS one.

```kotlin
val assessment: InvoiceAssessment = invoiceAnalyzer.analyze(document)
```

Business code does **not** parse model JSON, select provider SDKs, perform
prompt extraction, evaluate security rules, or implement approval state
machines. All of that happens inside TramAI, behind the typed boundary.

## The approval sequence (why the HTTP story matters)

```
POST /invoices/analyze             POST /approvals/{id}/approve
        ↓                                   ↓
   HTTP 202 AWAITING_APPROVAL       workflow resumes, payment 0 → 1
        ↓
   request is finished
```

> The HTTP request is finished. The workflow isn't.

Approval ids and challenge tokens are unique per suspension; the token is
stored in `PendingApprovalRegistry` and never appears in any HTTP response.
A duplicate approve is rejected (409) without double execution; a deny makes
the runtime itself refuse continuation (409) and the ledger gains nothing.

## What is real vs. fake

| Piece | Real TramAI | Conference simulation |
|---|---|---|
| Structured output engine | ✅ | |
| Policy engine + classification routing | ✅ | |
| Approval suspension + continuation | ✅ | |
| Audit chain + evidence pack | ✅ | |
| LLM | | ✅ deterministic `ScriptedProvider` |
| Payment side effect | | ✅ deterministic in-memory ledger |

More precisely: the model and the payment side effect are deterministic
simulations. Structured output, routing policy, tool governance,
approval/continuation, and audit/evidence semantics are real TramAI.
The simulations sit at the outermost edge — everything downstream of them
is the real library. See [CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md) for what
that does and doesn't prove.
