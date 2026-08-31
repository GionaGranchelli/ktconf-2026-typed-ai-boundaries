# task-004 — EU_CLOUD managed inference

Status: `DONE`
Track: D
Milestone: M3
Depends on: none
Blocks: task-006

## Objective

Provide a truthful, provider-neutral EU execution boundary through a managed
European OpenAI-compatible inference service. The active P0 implementation is
Scaleway Generative APIs with Mistral Small 24B. Mistral is temporary
infrastructure/model unblocker and is not NVIDIA, Nemotron, or NIM.

The provider/model composition is replaceable; `EU_CLOUD` remains the TramAI
trust-zone governance concept and must not become a synonym for Scaleway or
Mistral.

## Active path

`Scaleway Generative APIs` -> European deployment -> Mistral Small 24B ->
OpenAI-compatible API -> `EU_CLOUD` TramAI provider.

## Nebius disposition

Nebius was attempted and abandoned for P0. H200 Serverless attempts failed
with `code=13`; small Nemotron NIM attempts reached workload initialization on
H200 and H100 and then failed with `code=13`; H200 and H100 Compute attempts
also failed with `code=8 NotEnoughResources`. No successful inference response
was obtained. The exact NIM image, NGC account/key, registry access, model
metadata download, and NIM profile selection were independently proven
outside Nebius. Nebius remains documented as a possible future NVIDIA/Nemotron
route, not the active EU provider.

## Scope

- Rename the active EU provider/model to truthful neutral identities.
- Preserve an OpenAI-compatible adapter surface for task-006.
- Add deterministic allow/deny and counter tests for the EU provider seam.
- Add an opt-in direct and typed Scaleway smoke script.
- Document Scaleway setup, structured-output behavior, limitations, and the
  future Nemotron upgrade path.

Known limitation: the Scaleway deployment is temporary and uses Mistral; a
future EU Nemotron deployment is a separate infrastructure upgrade.

## Non-goals

- Do not claim legal sovereignty or regulatory compliance merely because the region is in France.
- Do not commit Nebius/NGC credentials.
- Do not build vendor-specific authorization logic in application code.
- Do not claim Scaleway/Mistral is NVIDIA, Nemotron, or NIM.
- Do not make tests depend on live Scaleway access.

## Acceptance criteria

- [x] Active provider is truthfully named for Scaleway and declared `EU_CLOUD`.
- [x] Existing OpenAI-compatible and counting composition is reused.
- [x] Deterministic tests pass without network or credentials.
- [x] Confidential -> EU_CLOUD succeeds through deterministic fixture/provider.
- [x] Restricted forced EU is denied before provider invocation, delta `0`.
- [x] Local/global providers and deterministic KTConf paths remain green.
- [x] Nebius failure history and Scaleway setup are documented separately.
- [x] Real smoke is marked separately and only after `/models`, direct chat,
  typed application, and counter proofs succeed.

## Handoff

Provide branch/head, changed files, old -> new provider names, configuration
variables, deterministic test commands/results, real smoke status, counter
evidence, documentation changes, remaining infrastructure blockers, and the
minimal configuration/code change required for a later EU Nemotron provider.
