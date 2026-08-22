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

## The boundaries the audience should remember

Two layers, deliberately distinct:

- **Application port:** `InvoiceAnalyzer` — the adapter through which the
  application service talks to AI. Good architecture, but a Spring detail.
- **The actual TramAI typed AI boundary:** the `@AiService`
  `InvoiceAnalysisService` contract — `ClassifiedDocument<InvoiceDocument>`
  → `InvoiceAssessment`. This is where the typed-boundary argument becomes
  concrete: the model's nondeterminism stops at a compile-time-typed
  surface, and the conference audience should remember THIS one.

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
