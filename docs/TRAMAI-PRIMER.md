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

The application decides *what* the model may see and *what it may do*. TramAI
enforces *whether that is allowed* — deterministically, every time.

## The six concepts

### 1. `@AiService`

A typed Kotlin contract describing how application code interacts with AI.

```kotlin
@AiService
interface InvoiceAnalysisService {
    suspend fun analyze(
        document: ClassifiedDocument<InvoiceDocument>
    ): InvoiceAssessment
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
consequences from it. The demo enforces one such consequence in
`CloudRoutingConfiguration.kt`: RESTRICTED data may not be sent to a
`GLOBAL_CLOUD` provider.

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
from URLs. `demo real` demonstrates this: the real-model provider is LOCAL
because the operator declares it LOCAL, never because of the endpoint
string.

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
permission to *execute* it. The application declares the capability in
`SchedulePaymentTool.kt`; TramAI decides whether the model's request may
proceed.

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

Every enforcement decision is an audit event, hash-chained to its
predecessor. One approval shows exactly four events:

```
APPROVAL_SUSPENDED
BEFORE_WORKFLOW_RESUME
APPROVAL_RESUMED
APPROVAL_COMPLETED
```

These are not strings fabricated for the presentation. They are retrieved
from TramAI's audit store for that workflow, and their hash chain is
verified (`GET /approvals/{id}/evidence` → `chainValid: true`).

## Who is responsible for what?

The single most useful table for integrating TramAI into another
application:

| Concern | Spring application | TramAI |
|---|---|---|
| Decide invoice classification | ✅ | |
| Declare provider trust zone | ✅ | |
| Define typed service contract | ✅ | |
| Configure tool capability | ✅ | |
| Call model provider | | ✅ |
| Generate/validate structured output | | ✅ |
| Enforce classification/provider policy | | ✅ |
| Decide whether tool needs approval | | ✅ |
| Suspend workflow | | ✅ |
| Store HTTP mapping for pending approval | ✅ | |
| Authorize continuation | | ✅ |
| Prevent consumed continuation reuse | | ✅ |
| Produce audit events | | ✅ |
| Execute business payment implementation | ✅ | |
| Make a real bank transfer | ❌ demo doesn't | ❌ |

TramAI is the governance engine. The application still owns everything that
is business-specific: classification, contracts, trust-zone assertions,
tool implementations, and the HTTP mapping of pending approvals.

## One request, end to end

Follow `KTCONF-PAY-001` through the system. Every step links its source
file.

| # | Step | Where |
|---|---|---|
| 1 | `POST /invoices/analyze` | [InvoiceController.kt](../app/src/main/kotlin/dev/giona/ktconf/api/InvoiceController.kt) |
| 2 | Application service classifies + delegates | [InvoiceApplicationService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceApplicationService.kt) |
| 3 | Application port | [InvoiceAnalyzer.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceAnalyzer.kt) |
| 4 | Typed `@AiService` boundary | [InvoiceAnalysisService.kt](../app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt) |
| 5 | TramAI invokes the provider | [ScriptedProvider.kt](../app/src/main/kotlin/dev/giona/ktconf/demo/ScriptedProvider.kt) |
| 6 | Model result: `HIGH` / `SCHEDULE_PAYMENT` | [DemoResponses.kt](../app/src/main/kotlin/dev/giona/ktconf/demo/DemoResponses.kt) |
| 7 | Tool requested | [SchedulePaymentTool.kt](../app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt) |
| 8 | TramAI sees `HIGH` + `HUMAN_REQUIRED` | policy inside TramAI |
| 9 | Workflow suspended | approval/continuation stores in [TramaiConfiguration.kt](../app/src/main/kotlin/dev/giona/ktconf/governance/TramaiConfiguration.kt) |
| 10 | Application service catches suspension → HTTP 202 | [InvoiceApplicationService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/InvoiceApplicationService.kt) |
| 11 | Server retains challenge token | [PendingApprovalRegistry.kt](../app/src/main/kotlin/dev/giona/ktconf/application/PendingApprovalRegistry.kt) |
| 12 | `POST /approvals/{id}/approve` | [ApprovalController.kt](../app/src/main/kotlin/dev/giona/ktconf/api/ApprovalController.kt) |
| 13 | `ResumeApprovalCommand` constructed | [ApprovalService.kt](../app/src/main/kotlin/dev/giona/ktconf/application/ApprovalService.kt) |
| 14 | TramAI validates + resumes continuation | same runtime as step 5 |
| 15 | `SchedulePaymentTool` executes | [SchedulePaymentTool.kt](../app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt) |
| 16 | HTTP 200 `InvoiceAssessment` | back through the typed boundary |

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

The `broken` instance proves the runtime half: the model produces garbage,
TramAI rejects it (HTTP 422), and no side effect executes. The boundary
holds even when the model misbehaves — which is exactly the claim of the
talk.
