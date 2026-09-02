# task-016 — Scaleway serverless Generative APIs EU provider

Status: `DONE`
Track: Post-freeze / infrastructure substitution
Owner branch: `contest/gtc-nvidia-submission`
Depends on: task-004, task-006

## Objective

Switch the temporary `EU_CLOUD` implementation from the costly Scaleway
dedicated deployment to the operator's Scaleway Generative APIs serverless
deployment, without changing TramAI governance or the OpenAI-compatible
provider composition.

## Target

```text
Scaleway Generative APIs serverless
→ configured deployment URL under api.scaleway.ai
→ Mistral Medium 3.5 128B
→ OpenAI-compatible `/v1`
→ eu-scaleway-provider / EU_CLOUD
```

The endpoint URL and model ID remain configuration values. Authentication uses
the operator's `SCW_SECRET_KEY` environment variable; its value must never be
logged or committed.

## Scope

- Accept `SCW_SECRET_KEY` as the serverless API-key fallback while retaining
  namespaced configuration overrides.
- Update the Scaleway smoke and combined real-boundary runner to use the new
  credential convention without echoing secrets.
- Verify `/v1/models`, direct chat, and the typed EU application route.
- Update Scaleway and GTC state documentation to describe serverless pricing/
  availability limitations truthfully.

## Non-goals

- Do not change `EU_CLOUD`, routing, provider identity, or TramAI policy.
- Do not commit the deployment identifier, secret value, or endpoint token as a
  credential; use operator configuration only.
- Do not make serverless inference a deterministic test dependency.
- Do not claim NVIDIA/Nemotron/NIM for the Mistral service.

## Acceptance criteria

- [x] Existing deterministic tests remain green with all provider variables
      absent.
- [x] The serverless key convention is supported by the opt-in smoke scripts.
- [x] `/models` advertises the configured model.
- [x] Direct chat and typed `EU_CLOUD` application inference succeed.
- [x] Forced `RESTRICTED → EU_CLOUD` remains denied with provider delta `0`.
- [x] Documentation identifies serverless Generative APIs, the configured
      model, and the limitation that availability/cost is usage-dependent.

## Evidence

Live verification on 2026-09-02: the configured serverless `/v1/models`
endpoint returned HTTP 200 and advertised `mistral-medium-3.5-128b`; direct
chat returned HTTP 200; the typed application route returned HTTP 200 with
`selectedRoute=EU_CLOUD` and invocation delta `1`; forced
`RESTRICTED -> EU_CLOUD` returned HTTP 403 with
`classification-routing-blocked` and invocation delta `0`. The API key and
deployment identifier were supplied only through the operator environment and
are not recorded here.

Deterministic verification is recorded in the task handoff; the serverless
path remains opt-in and is not a test dependency.
