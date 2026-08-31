# Claims boundary

What this demo proves — and what it deliberately does **not** claim.

## Proved (deterministic, reproducible, tested)

- The pinned TramAI revision exposes `ProviderTrustZone.LOCAL`, `EU_CLOUD`,
  and `GLOBAL_CLOUD` as first-class governance values. Its enabled sovereign
  routing matrix allows PUBLIC/INTERNAL in all zones, CONFIDENTIAL in LOCAL or
  EU_CLOUD, and RESTRICTED in LOCAL only; `DefaultPolicyEngine` checks this
  before provider invocation. This is a policy capability, not proof that an
  operator's EU deployment is legally sovereign or compliant.

- TramAI structured output turns a valid model response into a typed
  `InvoiceAssessment` — no manual JSON mapping in application code.
- TramAI's classification-aware provider policy enforcement **denies** a `RESTRICTED`
  document on a `GLOBAL_CLOUD` provider BEFORE provider invocation (HTTP 403,
  `reasonCode=classification-routing-blocked`, cloud invocation **delta** 0 —
  even after the same provider was used by earlier requests) and allows the
  same document on a LOCAL provider. The demo proves this with a
  deliberately misrouted request through the SAME runtime
  (`/invoices/boundary/restricted-cloud`, printed before/after counts).
- The contest `GLOBAL_NVIDIA` operation also proves RESTRICTED denial before
  provider invocation with global invocation delta `0`. Missing or malformed
  trusted PDF metadata fails at the multipart boundary with all provider
  counters unchanged.
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
  In this demo the request's classification represents a **trusted upstream
  governance fact**: the caller is expected to be a trusted component that
  already decided the classification. Nothing stops an arbitrary external
  caller from saying PUBLIC; the demo shows what TramAI does with a wrong
  route once the classification is set, not how to trust a self-classifying
  external user.
- **Synthetic PDF metadata is not a signature.** The contest PDF path reads
  the required classification/residency properties locally and rejects
  contradictory combinations before analysis. The embedded properties are a
  demo trust contract, not a cryptographic signature, Microsoft Purview label,
  or legal/compliance assertion.
- **No production audit infrastructure.** Stores are in-memory for the
  demo; they are real TramAI stores, not fakes, but they are not durable.
- **No claim that these TramAI APIs are stable.** The exact API surface is
  pinned to a specific revision (`docs/TRAMAI-INTEGRATION.md`).

## Real-model path (optional, opt-in, off-stage)

- The contest-only `global-nvidia-provider` uses `KTCONF_GTC_GLOBAL_NVIDIA_*`
  configuration and maps the logical `global-nvidia-invoice-model` to the
  deployment model `nvidia/nemotron-3.5-lightning-30b-a3b`. It is counted and
  deterministic when no NVIDIA key is supplied. A real HTTP 200 typed result
  is recorded in `docs/gtc/evidence/global-nvidia-smoke.md`; final-head
  refreshes remain required for the evidence freeze.

- Proves: the actual two-provider architecture behind the SAME typed
  contract (`ClassifiedDocument<InvoiceDocument>` → `InvoiceAssessment`),
  with the same structured-output validation:
  - `LOCAL` → Qwen3.8-27B-UD-Q6_K on the z840 (Tailscale), `KTCONF_DEMO_LOCAL_*`
  - `CLOUD` → DeepSeek V4 Flash, `KTCONF_DEMO_CLOUD_*`
  `ModelAliasProvider` maps each logical route name to the real endpoint
  model id; the logical names (`local-invoice-model`/`cloud-invoice-model`)
  are never sent to the models.
- **Trust zone is an operator assertion, not a URL property.** The repo
  declares the z840 endpoint LOCAL and DeepSeek GLOBAL_CLOUD by
  configuration, never from URLs or provider types.
- `preflight`, `rehearse`, `stress-rehearse` and `stage-up` **unset BOTH**
  provider env families, so the deterministic oracle never silently depends
  on a network model — a stray DeepSeek key in the operator's shell cannot
  reach the conference stage.
- `preflight-real` is the ONLY entry point that exercises the real path
  (off-stage): it proves each configured identity produces a typed 200 with
  the expected selectedRoute, and — when cloud is real — that a RESTRICTED
  request forced to cloud is denied BEFORE DeepSeek is called (cloud
  invocation delta 0).
- The real path proves the typed contract and boundary behavior against real
  models. The task-007 contest claim additionally requires a real local
  Nemotron payment proposal and real workflow audit evidence; deterministic
  payment tests are not a substitute for that pending live proof.

## Evidence rule

Every evidence row identifies its source: deterministic test assertions,
provider counters, or a sanitized live-provider artifact. Audit-chain rows
derive from real TramAI audit records; routing and fail-closed rows derive from
their executable HTTP tests and counter assertions. Nothing is invented for
the show. If a scenario cannot be demonstrated with real TramAI behavior, it
is not demonstrated.

## Governance / EU AI Act note

This demo shows technical controls such as runtime policy, traceability,
human approval, provider restrictions and structured failure boundaries
that may support organisational governance and compliance work. It does not
establish EU AI Act compliance, conformity assessment, certification, or
legal suitability for any specific AI system.
