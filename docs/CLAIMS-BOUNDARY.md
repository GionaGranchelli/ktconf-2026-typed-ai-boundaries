# Claims boundary

What this demo proves — and what it deliberately does **not** claim.

## Proved (deterministic, reproducible, tested)

- TramAI structured output turns a valid model response into a typed
  `InvoiceAssessment` — no manual JSON mapping in application code.
- TramAI's classification-aware routing matrix **denies** a `RESTRICTED`
  document on a `GLOBAL_CLOUD` provider BEFORE provider invocation (HTTP 403,
  `reasonCode=classification-routing-blocked`, `cloudInvocationCount` stays
  0) and allows the same document on a LOCAL provider. The demo proves this
  with a deliberately misrouted request through the SAME runtime
  (`/invoices/boundary/restricted-cloud`).
- TramAI's structured-output engine rejects invalid model output through the
  SAME application and SAME runtime (HTTP 422, `structured-output-rejected`),
  and no side effect executes. Nothing about the application changed — only
  the model response.
- TramAI's approval machinery suspends a HIGH-risk tool request before
  execution; resume goes through the real continuation mechanism (HTTP 202
  → approve → 200; duplicate approve → 409).
- Exactly-once in the demo scope: after approval the ledger executes once,
  and a duplicate resume attempt is rejected without double execution. The
  ledger deduplicates on the engine-supplied idempotency key.
- Denial keeps the ledger at zero: the runtime itself refuses continuation
  after a deny (HTTP 409 on resume), so the payment counter does not move.
- TramAI emits hash-chained audit records; the chain verifies
  (`AuditChainVerifier`), evidence is per workflow
  (`GET /approvals/{id}/evidence` proves exactly the 4 ordered events for
  that execution), and a machine-readable evidence pack is written.
- Approval challenge tokens are unique per suspension and never leave the
  server; only `approvalId` + `workflowRunId` are exposed over HTTP.
- Concurrent approval workflows do not cross state: distinct ids, distinct
  workflow runs, per-workflow evidence streams.
- One `SovereignTramaiRuntime` bean, two simultaneous provider routes, and
  the `SchedulePaymentTool` is collected from Spring by the sovereign
  starter (upstream tramAI PR #268). Zero manual sovereign construction.

## NOT claimed

- **No automatic provider selection.** The application chooses the route
  (`when (classification)`). TramAI 0.6.x validates whether the chosen route
  is allowed; it does NOT automatically choose LOCAL for RESTRICTED input.
  Policy-aware provider selection is future (0.7) roadmap.
- **No globally exactly-once distributed execution.** The demo proves
  exactly-once for this single-process, single-ledger scenario only.
- **No real LLM behavior in the deterministic instances.** The model
  providers behind the stage are deterministic and scripted. Nothing in the
  deterministic scenarios evaluates model quality or nondeterminism.
- **No automatic DLP/classification.** Classification is supplied by the
  request/caller — TramAI never infers confidentiality from the payload.
  In production classification could come from upstream metadata, DLP, a
  deterministic classifier, a policy engine, or explicit workflow state.
- **No production audit infrastructure.** Stores are in-memory for the
  demo; they are real TramAI stores, not fakes, but they are not durable.
- **No claim that these TramAI APIs are stable.** The exact API surface is
  pinned to a specific revision (`docs/TRAMAI-INTEGRATION.md`).

## Real-model path (optional, opt-in)

- Proves: an actual LLM sits behind the same typed input/output contract
  (`ClassifiedDocument<InvoiceDocument>` → `InvoiceAssessment`), with the
  same structured-output validation.
- **Trust zone is an operator assertion, not a URL property.** This repo
  declares the real provider LOCAL by configuration, so
  `KTCONF_DEMO_LOCAL_BASE_URL` must point to an endpoint the operator
  intentionally treats as LOCAL (Ollama on the laptop, private LAN,
  self-hosted inference). Public cloud APIs must not be declared LOCAL.
- `preflight` and `stage-up` **unset** the real-model environment variables,
  so the deterministic oracle never silently depends on a network model.
  `preflight-real` is the only entry point that requires them.
- The real-model path proves only the typed contract against a real model.
  It is NOT needed to demonstrate payment, approval, denial, evidence or
  exactly-once behavior — those remain deterministic.

## Rule

Every row in the evidence output derives from a real audit record.
Nothing is invented for the show. If a scenario cannot be demonstrated with
real TramAI behavior, it is not demonstrated.

## Governance / EU AI Act note

This demo shows technical controls such as runtime policy, traceability,
human approval, provider restrictions and structured failure boundaries
that may support organisational governance and compliance work. It does not
establish EU AI Act compliance, conformity assessment, certification, or
legal suitability for any specific AI system.
