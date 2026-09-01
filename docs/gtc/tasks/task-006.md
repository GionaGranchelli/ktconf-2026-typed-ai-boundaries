# task-006 — Integrate three governed execution boundaries

Status: `DONE`
Track: F
Milestone: M5
Depends on: task-001, task-002, task-003, task-004, task-005
Blocks: task-007, task-008

## Objective

Integrate the proven LOCAL NVIDIA, temporary EU_CLOUD Scaleway/Mistral, and GLOBAL NVIDIA paths into one Spring application and one TramAI governance plane while preserving deterministic CI and KTConf behavior. A later EU Nemotron deployment must not change governance semantics.

## Scope

- Add/validate contest-specific provider identities/configuration for all three real routes.
- Wire the real PDF flow into placement authorization.
- Keep provider trust zones explicit configuration.
- Preserve `CountingModelProvider` or equivalent per-route proof counters.
- Add a deliberate wrong-route path for the EU-confidential document and prove denial before GLOBAL invocation.
- Add a RESTRICTED path proving EU and GLOBAL denial, LOCAL success.
- Keep automatic selection vs validation semantics honest and documented.

## Required primary scenarios

1. PUBLIC -> GLOBAL_CLOUD -> real Build.NVIDIA.com Nemotron -> typed result.
2. CONFIDENTIAL + EU -> GLOBAL proposed -> denied, GLOBAL delta 0 -> EU_CLOUD -> Scaleway/Mistral -> typed result.
3. RESTRICTED -> EU/GLOBAL denied -> LOCAL NVIDIA RTX with Qwen
   `qwen/qwen3.8-27b` -> typed result.

## Acceptance criteria

- [x] One process/runtime exposes all three logical providers.
- [x] Deterministic LOCAL NVIDIA, temporary EU Scaleway/Mistral, and GLOBAL NVIDIA allowed paths succeed.
- [x] Individual real provider smoke proofs exist for all three boundaries.
- [x] One combined real PDF run across all three providers passed; sanitized
  evidence is recorded in `docs/gtc/evidence/combined-real-boundaries.md`.
- [x] Denied route counters remain delta `0`.
- [x] Trust zone is not inferred from URL/vendor.
- [x] No vendor-specific residency checks bypass TramAI.
- [x] Deterministic offline tests continue to pass.
- [x] KTConf scripts/config remain usable.
- [x] Logs/evidence expose route/model/provider/zone but no document content or secrets.

## Handoff

Provide an architecture diff, provider/model mapping table, scenario outputs, counter proofs, tests, config keys, and unresolved real-provider reliability risks.

Closure evidence: the trusted PDF contract now accepts only the policy-aligned
combinations PUBLIC/INTERNAL + ANY, CONFIDENTIAL + EU_ONLY, and RESTRICTED +
LOCAL_ONLY. Trusted PDF residency selects `GLOBAL_CLOUD` for PUBLIC/ANY,
`EU_CLOUD` for CONFIDENTIAL/EU_ONLY, and `LOCAL_NVIDIA` for
RESTRICTED/LOCAL_ONLY. The deterministic application test proves one allowed
invocation per boundary. The forced confidential-EU-to-GLOBAL PDF proof and
restricted EU/GLOBAL tests prove denial before invocation with delta `0`, then
the same confidential PDF succeeds in EU. Real provider evidence remains in
`docs/gtc/evidence/`. The historical task-003 Nemotron proof covers local typed
inference; the stable live LOCAL model for the combined and action flows is
Qwen `qwen/qwen3.8-27b` on NVIDIA RTX. The combined real PDF run passed and is
recorded in `docs/gtc/evidence/combined-real-boundaries.md`.
