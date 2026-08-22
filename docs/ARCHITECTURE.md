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
                                         │ application domain           │
                                         │ AI integration               │
                                         │ runtime configuration        │
                                         │ business capabilities        │
                                         └──────────────────────────────┘
```

No conference code contaminates TramAI, and no presentation-specific hacks
become library APIs.

## Application layers (this repo)

```
domain/          InvoiceDocument, InvoiceAssessment, DemoInvoices
                 ↑ typed input / typed result — the audience sees THIS
ai/              InvoiceAnalysisService (@AiService boundary)
                 ScriptedProvider / DemoResponses (the only fakes)
runtime/         DemoRuntimeFactory — real SovereignTramai wiring
tools/           SchedulePaymentTool (security metadata on the tool)
                 InMemoryPaymentLedger (exactly-once via idempotency key)
scenarios/       the six demo scenarios — assert expected TramAI behavior
presentation/    stage formatting
cli/             stable stage API: ./scripts/demo <command>
```

## The one boundary the audience should remember

```kotlin
val assessment: InvoiceAssessment =
    invoiceAnalysisService.analyze(invoice)
```

Business code does **not** parse model JSON, select provider SDKs, perform
prompt extraction, evaluate security rules, or implement approval state
machines. All of that happens inside TramAI, behind the typed boundary.

## What is real vs. fake

| Piece | Real TramAI | Conference fake |
|---|---|---|
| Structured output engine | ✅ | |
| Policy engine + classification routing | ✅ | |
| Approval suspension + continuation | ✅ | |
| Audit chain + evidence pack | ✅ | |
| LLM | | ✅ deterministic `ScriptedProvider` |
| Invoice data | | ✅ fictional fixtures |

The fake provider is the *only* fake, and it sits at the outermost edge —
everything downstream of it is the real library. See
[CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md) for what that does and doesn't prove.
