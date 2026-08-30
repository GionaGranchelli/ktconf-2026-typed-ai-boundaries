# task-001 — Native regional trust semantics

Status: `READY`
Track: A
Milestone: M0
Depends on: none
Blocks: task-006

## Objective

Determine whether the pinned TramAI revision already supports a native regional trust zone suitable for `EU_CLOUD`. If not, implement the smallest correct governance-layer capability so placement policy is owned by TramAI rather than by application-specific Nebius checks.

## Scope

- Inspect the pinned TramAI submodule/API for `ProviderTrustZone` and routing-matrix semantics.
- If an EU/regional zone exists, document and prove the exact classification matrix needed by the GTC demo.
- If absent, implement the minimum upstream TramAI change with exhaustive tests and update the pinned revision only through the normal submodule process.
- Preserve existing `LOCAL` and `GLOBAL_CLOUD` semantics.

## Required policy outcome

Target matrix:

| Data classification / residency | LOCAL | EU_CLOUD | GLOBAL_CLOUD |
|---|---:|---:|---:|
| PUBLIC | allow | allow | allow |
| INTERNAL | allow | allow | allow only if current TramAI policy explicitly permits it |
| CONFIDENTIAL + EU residency | allow | allow | deny |
| RESTRICTED | allow | deny | deny |

Do not invent semantics that contradict current TramAI classification rules; if a conflict exists, document it and propose the smallest explicit policy extension.

## Non-goals

- Do not special-case `Nebius`, URLs, DNS names, or cloud vendors in routing policy.
- Do not implement automatic provider selection unless strictly required.
- Do not weaken deny-by-default behavior.

## Acceptance criteria

- [ ] Exact current TramAI trust-zone capabilities documented.
- [ ] `EU_CLOUD` is represented as a first-class governance concept or an equivalent existing primitive is proven sufficient.
- [ ] Exhaustive allow/deny tests cover all relevant classification × zone combinations.
- [ ] Disallowed route is denied before provider invocation.
- [ ] Existing LOCAL/GLOBAL tests remain green.
- [ ] Application code contains no vendor-specific residency authorization logic.
- [ ] Claims documentation clearly distinguishes policy semantics from infrastructure assurances.

## Verification

Run the narrow TramAI routing/security tests first, then the full affected TramAI module suite, then the demo repository deterministic preflight after repinning.

Record exact commands and test counts in the handoff.

## Handoff

Report:

- capability found/added;
- exact API names;
- routing matrix;
- changed files/commits;
- test evidence;
- any compatibility risk for the KTConf path.
