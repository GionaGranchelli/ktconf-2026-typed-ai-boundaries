# TramAI in 10 minutes

A conceptual primer for someone arriving from the talk who has never seen
TramAI. Not API documentation — just enough to understand what is actually
happening when a request enters this repository.

## What is TramAI?

TramAI is a Kotlin/JVM runtime for putting explicit boundaries around model
execution. Application code declares typed AI services, model/provider
configuration, data classification and tool capabilities. TramAI handles
model execution, structured-output validation, policy enforcement, approval
suspension/resumption and audit evidence before results or side effects
cross those boundaries.

```
Spring application
       │
       ▼
 typed @AiService
       │
       ▼
     TramAI
  ┌────┼───────────────┐
  │    │               │
types policy        tools/approval
  │    │               │
  └────┴──────┬────────┘
              ▼
           provider
              ▼
             LLM
```

The application declares classifications, trust assumptions and
capabilities. TramAI evaluates and enforces those rules at runtime —
deterministically, every time.

## The six concepts

### 1. `@AiService`

A typed Kotlin contract describing how application code interacts with AI.

```kotlin
@AiService
interface InvoiceAnalysisService {
    @Operation(model = "local-invoice-model", tools = ["schedule-payment"])
    suspend fun analyzeLocal(document: ClassifiedDocument<InvoiceDocument>): InvoiceAssessment

    @Operation(model = "cloud-invoice-model")
    suspend fun analyzeCloud(document: ClassifiedDocument<InvoiceDocument>): InvoiceAssessment
}
```

What Kotlin sees is `InvoiceAssessment`. What the model actually produces is
tokens/JSON. TramAI sits between them:

```
tokens
  ↓
structured validation
  ↓
deserialization
  ↓
InvoiceAssessment
```

Application code never parses model JSON. That distinction is the whole
point of the talk.

### 2. `ClassifiedDocument<T>`

Application-provided data plus a classification that policy can reason about:

```
InvoiceDocument
+
RESTRICTED
+
DECLARED
```

TramAI does **not** magically discover that an invoice is confidential. The
application supplies the classification; TramAI deterministically enforces
consequences from it. In this demo every request carries an explicit
classification. In production it could come from upstream metadata, DLP, a
deterministic classifier, a policy engine, or explicit workflow state.

### 3. Provider trust zone

Every provider is declared as `LOCAL`, `GLOBAL_CLOUD`, or similar — a
statement about deployment reality:

```
RESTRICTED
     +
GLOBAL_CLOUD
     ↓
DENY
     ↓
provider invocation = 0
```

Trust zones are **application/operator assertions**, not values inferred
from URLs. The sovereign policy matrix (from `application.yml`'s
`provider-zones`) enforces: RESTRICTED → LOCAL only.

### 4. Tool

A tool is *authority*, not just a function. In this repository:

```
schedule-payment

permission = payment.schedule
risk       = HIGH
effect     = WRITE
approval   = HUMAN_REQUIRED
```

The model may *request* this capability. It does not thereby receive
permission to *execute* it. `SchedulePaymentTool` is a normal Spring bean
implementing `TramaiTool`; the sovereign starter collects it automatically
(upstream tramAI PR #268).

### 5. Approval / continuation

The heart of the demo:

```
Model requests schedule-payment
              ↓
       TramAI evaluates policy
              ↓
        APPROVAL REQUIRED
              ↓
     workflow is suspended
              ↓
         HTTP returns 202
              ↓
      human approves later
              ↓
      TramAI validates approval
              ↓
      continuation resumes
              ↓
       payment tool executes
```

Why does this repository need `PendingApprovalRegistry` if TramAI handles
approval?

> TramAI owns approval and continuation semantics. The Spring application
> owns the HTTP transport concern of associating a public `approvalId` with
> the server-side challenge token needed to resume that continuation. The
> challenge token is never exposed to the client.

### 6. Audit / evidence

Governed enforcement points can emit hash-chained audit events. This demo
proves the approval lifecycle below — one approval yields exactly four
events, each chained to its predecessor:

```
APPROVAL_SUSPENDED
BEFORE_WORKFLOW_RESUME
APPROVAL_RESUMED
APPROVAL_COMPLETED
```

These are not strings fabricated for the presentation. They are retrieved
from TramAI's audit store for that workflow, and their hash chain is
verified (`GET /approvals/{id}/evidence` → `chainValid: true`).

## How `@AiService` becomes executable

An `@AiService` interface describes a contract. The Spring Boot sovereign
starter turns it into a working implementation:

```
@AiService interface
       │
       │ describes the contract
       ▼
application.yml (tramai.sovereign.*)
       │
       │ allowed models, providers, zones, tools, permissions
       ▼
sovereign starter auto-configuration
       │
       │ collects ModelProvider beans + TramaiTool beans
       ▼
SovereignTramaiRuntime (auto-configured Spring bean)
       │
       ▼
runtime.create(InvoiceAnalysisService::class)
       │
       ▼
executable typed service
```

Two short definitions:

> **SovereignTramai** is the configured governed environment: allowed
> models/providers/tools, trust zones, policy infrastructure, approval and
> audit stores.

> **SovereignTramaiRuntime** is the live runtime created from that
> configuration. It creates the executable `@AiService` implementation and
> is also used later to resume suspended approvals.

The conference application constructs **zero** of these classes: the starter
owns them all (`SovereignTramaiProperties` in `application.yml`). There is
exactly ONE runtime bean per Spring context.

## Who is responsible for what?

The single most useful table for integrating TramAI into another
application:

| Concern | Spring application | TramAI |
|---|---|---|
| Supply data classification | ✅ | |
| Declare provider trust zone | ✅ | |
| Define typed service contract | ✅ | |
| Declare tool permission/risk/effect/approval metadata | ✅ | |
| Choose the normal model route (when) | ✅ | |
| Evaluate/enforce tool policy and approval requirement | | ✅ |
| Call model provider | | ✅ |
| Generate/validate structured output | | ✅ |
| Enforce classification/provider policy | | ✅ |
| Suspend workflow | | ✅ |
| Store HTTP mapping for pending approval | ✅ | |
| Authorize continuation | | ✅ |
| Prevent consumed continuation reuse | | ✅ |
| Produce audit events | | ✅ |
| Execute business payment implementation | ✅ | |
| Make a real bank transfer | ❌ demo doesn't | ❌ |

TramAI is the governance engine. The application still owns everything that
is business-specific: classification, contracts, trust-zone assertions,
route choice, tool implementations, and the HTTP mapping of pending
approvals.

## One request, end to end

Follow `KTCONF-PAY-001` through the system. Every step links its source
file.

| # | Step | Where |
|---|---|---|
| 1 | `POST /invoices/analyze` with explicit `classification=RESTRICTED` | [InvoiceController.kt](../app/src/main/kotlin/dev/giona/ktconf/api/InvoiceController.kt) |
| 2 | Request → `ClassifiedDocument` (DECLARED) | [InvoiceService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceService.kt) |
| 3 | Routing `when`: RESTRICTED → `analyzeLocal` | [InvoiceService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceService.kt) |
| 4 | Typed `@AiService` boundary (model=local-invoice-model, tools=[schedule-payment]) | [InvoiceAnalysisService.kt](../app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt) |
| 5 | TramAI validates the route, then invokes the provider | policy inside TramAI |
| 6 | Model result: tool call `schedule-payment` | [DemoResponses.kt](../app/src/main/kotlin/dev/giona/ktconf/demo/DemoResponses.kt) |
| 7 | TramAI sees `HIGH` + `HUMAN_REQUIRED` → workflow suspended | approval machinery inside TramAI |
| 8 | Application registers the pending approval (server-side token) | [PendingApprovalRegistry.kt](../app/src/main/kotlin/dev/giona/ktconf/application/PendingApprovalRegistry.kt) |
| 9 | HTTP 202 `{approvalId, workflowRunId}` — no token | [InvoiceController.kt](../app/src/main/kotlin/dev/giona/ktconf/api/InvoiceController.kt) |
| 10 | `POST /approvals/{id}/approve` | [ApprovalController.kt](../app/src/main/kotlin/dev/giona/ktconf/api/ApprovalController.kt) |
| 11 | Transition + real `ResumeApprovalCommand` through the SAME runtime | [ApprovalService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/ApprovalService.kt) |
| 12 | `SchedulePaymentTool` executes (ledger exactly-once) | [SchedulePaymentTool.kt](../app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt) |
| 13 | HTTP 200 `InvoiceAssessment` | back through the typed boundary |

The trace shows the two systems the talk is built on:

> The HTTP request is finished. The workflow isn't.

## Compile-time vs runtime

The typed boundary is not magic. A Kotlin return type does not stop invalid
model output:

> Application code sees a compile-time-typed surface; TramAI validates model
> output at runtime before allowing it to cross that boundary.

```
compile time:  application expects InvoiceAssessment
runtime:       LLM output → schema validation → deserialization → InvoiceAssessment
```

The `invalid` scenario proves the runtime half through the SAME
application: the model produces garbage, TramAI rejects it (HTTP 422), and
no side effect executes. The boundary holds even when the model misbehaves —
which is exactly the claim of the talk.

## What 0.6.x does and does not do

TramAI 0.6.x **validates** whether the selected route/provider is allowed
for the classification. It does **NOT** automatically select LOCAL for
RESTRICTED input — the application chooses the route, and the 
`restricted-cloud` fault injection proves TramAI is the independent
enforcement backstop when the application's choice is wrong. Policy-aware
provider selection is future (0.7) roadmap work.
