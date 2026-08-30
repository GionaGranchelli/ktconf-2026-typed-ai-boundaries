# task-002 — GLOBAL NVIDIA hosted Nemotron

Status: `READY`
Track: B
Milestone: M1
Depends on: none
Blocks: task-006

## Objective

Prove real GLOBAL inference through the user's Build.NVIDIA.com access and integrate it behind the existing TramAI provider abstraction without disturbing deterministic CI.

## Target

- Base URL: `https://integrate.api.nvidia.com/v1`
- Initial model candidate: `nvidia/nemotron-3.5-lightning-30b-a3b`
- Trust zone: `GLOBAL_CLOUD`

Verify the current model identifier from Build.NVIDIA.com before hard-coding it.

## Scope

1. Perform a direct authenticated smoke test with the NVIDIA API key outside source control.
2. Confirm OpenAI-compatible request/response behavior required by TramAI.
3. Integrate through the existing provider abstraction (`OpenAiCompatibleProvider` / aliasing / counting seam where appropriate).
4. Add contest-specific configuration/env names rather than replacing the KTConf DeepSeek path.
5. Preserve invocation counters so pre-invocation denial can prove delta `0`.
6. Add a real-provider smoke command/test that is opt-in and secret-gated.

## Non-goals

- Do not remove DeepSeek support from the KTConf path.
- Do not put the NVIDIA API key in `.env.example`, tests, docs, logs, or command history committed to git.
- Do not make normal CI depend on NVIDIA availability.

## Acceptance criteria

- [ ] Direct NVIDIA hosted Nemotron smoke succeeds.
- [ ] Same call succeeds through the application provider abstraction.
- [ ] Typed TramAI result is produced from the real model.
- [ ] Provider identity remains logical and stable even if the remote model ID differs.
- [ ] Invocation counter increments on allowed calls.
- [ ] A governance denial can occur before the NVIDIA provider call and leave delta `0`.
- [ ] Deterministic CI/preflight remains offline and green.
- [ ] Secret scanning of changed files is clean.

## Verification

Provide sanitized evidence of:

- endpoint/model used;
- HTTP success;
- application typed result;
- before/after counter values;
- exact local verification commands with secret values redacted.

## Handoff

Document model ID, base URL, provider config keys, code paths changed, tests, and any NVIDIA API quirks relevant to task-006.
