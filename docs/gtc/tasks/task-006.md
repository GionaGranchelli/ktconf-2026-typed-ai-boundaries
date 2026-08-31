# task-006 — Integrate three governed execution boundaries

Status: `BLOCKED`
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
3. RESTRICTED -> EU/GLOBAL denied -> LOCAL RTX Nemotron -> typed result.

## Acceptance criteria

- [ ] One process/runtime exposes all three logical providers.
- [ ] LOCAL NVIDIA, temporary EU Scaleway/Mistral, and GLOBAL NVIDIA allowed real paths succeed.
- [ ] Denied route counters remain delta `0`.
- [ ] Trust zone is not inferred from URL/vendor.
- [ ] No vendor-specific residency checks bypass TramAI.
- [ ] Deterministic offline tests continue to pass.
- [ ] KTConf scripts/config remain usable.
- [ ] Logs/evidence expose route/model/provider/zone but no document content or secrets.

## Handoff

Provide an architecture diff, provider/model mapping table, scenario outputs, counter proofs, tests, config keys, and unresolved real-provider reliability risks.
