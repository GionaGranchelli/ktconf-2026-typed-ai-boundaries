# Agent execution guide — GTC NVIDIA submission

This guide is the operating contract for coding/research agents working on `contest/gtc-nvidia-submission`.

Read in this order before changing code:

1. `/GTC-2026-SUBMISSION.md`
2. `/docs/gtc/ROADMAP.md`
3. `/docs/gtc/ARCHITECTURE.md`
4. `/docs/gtc/NVIDIA-NEBIUS.md`
5. this file

Then inspect the existing KTConf documentation, especially:

- `/README.md`
- `/docs/CLAIMS-BOUNDARY.md`
- `/docs/ARCHITECTURE.md`
- `/docs/DEMO-SCRIPT.md`

## Mission

Do not redesign the project. Convert the proven KTConf governance application into a contest-quality NVIDIA deployment proof while preserving the deterministic oracle.

The branch must prove:

> **The application chooses a route. TramAI authorizes the boundary. Nemotron may propose an action. TramAI authorizes execution.**

## Branch safety

- Work only on `contest/gtc-nvidia-submission` or a child feature branch created from it.
- Do not merge contest-only changes into `main` during experimentation.
- Do not repin TramAI casually.
- If an upstream TramAI change is required, isolate it and document the exact commit/submodule update needed.
- Never delete or weaken current deterministic KTConf tests to make a contest path pass.

## Working style

Every implementation task should follow this sequence:

```text
1. inspect existing behavior
2. write/identify the discriminator test
3. make the smallest production change
4. run focused test
5. run relevant module suite
6. run contest gate affected by the change
7. update CURRENT-STATE / evidence
8. commit one coherent slice
```

Do not stack multiple unverified milestones in one commit.

## Definition of a valid agent result

A task is not "done" because code compiles.

A valid handoff must state:

```text
Milestone/task:
Branch/head:
Files changed:
Behavior added:
What remains simulated:
What is real:
Tests/gates executed:
Exact results:
Secrets required (names only):
Known risks/open questions:
Next dependency:
```

If a real provider was used, also record:

```text
provider identity
endpoint type (never secret/token)
model ID
runtime/container version
GPU model if self-hosted
region if cloud-hosted
proof that a forbidden route did not invoke the provider
```

## Parallel tracks

The project can use multiple agents, but assign ownership to minimize collisions.

### Track A — governance/trust-zone semantics

Owns:

- M0 trust-zone capability spike;
- any required TramAI upstream `EU_CLOUD`/regional semantics;
- policy matrix tests;
- three-provider governance integration.

Avoid editing provider transport code unless necessary.

### Track B — GLOBAL NVIDIA

Owns:

- Build.NVIDIA.com hosted API integration;
- NVIDIA global configuration/secrets contract;
- real global smoke script;
- global provider invocation counter proof.

Primary files likely include provider configuration, endpoint properties and real-provider scripts/tests.

### Track C — LOCAL NVIDIA

Owns:

- RTX + llama.cpp + Nemotron 3 Nano 4B infrastructure proof;
- local endpoint configuration;
- reproducibility notes;
- local real-provider smoke proof.

Do not change EU or global policy semantics.

### Track D — EU managed inference

Owns:

- Scaleway Generative APIs European deployment;
- OpenAI-compatible endpoint and model verification;
- endpoint auth and secret handling;
- EU provider smoke proof;
- infrastructure evidence.

The active P0 route is Scaleway/Mistral. The failed Nebius investigation is
historical and must not be resumed unless the task contract explicitly changes.

This track should first prove the endpoint independently of the Spring application.

### Track E — PDF + trusted metadata

Owns:

- safe synthetic PDF fixtures;
- metadata contract;
- local metadata parsing;
- upload API;
- fail-closed validation;
- proof that metadata authorization precedes provider invocation.

Do not add remote classification or OCR as a shortcut.

### Integration track

Starts only when A-D/E have independent green proofs.

Owns:

- full document -> provider -> typed result flow;
- forced-route denial scenarios;
- payment/approval path with a real local action model on NVIDIA RTX;
- contest scripts;
- evidence pack;
- demo rehearsal.

## File-collision discipline

Before editing a heavily shared file such as:

- `app/src/main/resources/application.yml`
- provider configuration classes
- endpoint configuration/property classes
- `InvoiceService.kt`
- sovereign/trust-zone configuration

an agent should inspect current branch HEAD and recent commits first.

Prefer additive files/config sections during parallel development and consolidate during integration.

## Security requirements

### Secrets

Never commit or echo values for:

- `NVIDIA_API_KEY` / contest global NVIDIA key;
- `NGC_API_KEY`;
- Nebius IAM credentials;
- Nebius endpoint bearer token;
- registry passwords;
- SSH private keys.

Use placeholders such as `<NVIDIA_API_KEY>` only in docs.

Add secret environment names to `.env.example` only with empty/example-safe values.

### Document data

Only synthetic contest PDFs belong in the repository.

Never put:

- real customer names;
- real invoices;
- personal data;
- proprietary company documents;

into fixtures, logs, screenshots or demo recordings.

### Logging

Governance logs may contain:

- classification;
- residency;
- logical model;
- provider identity;
- trust zone;
- safe reason code;
- correlation/workflow ID.

They should not contain:

- raw PDF content;
- Authorization headers;
- API keys/tokens;
- full prompts if they contain protected document data.

## Claims discipline

Agents must distinguish:

### Proved

A claim can be made when the repository has a repeatable test/evidence path.

Examples:

- provider invocation delta is zero for a denied route;
- Scaleway European deployment is recorded;
- configured EU model ID is recorded;
- OpenAI-compatible `/models` and chat proofs are recorded;
- payment ledger changed 0 -> 1 once;
- audit chain verifier passed.

### Inferred/assumed

Do not turn these into marketing claims without evidence:

- "sovereign cloud";
- "EU AI Act compliant";
- "data can never leave Europe" when only application routing was proven;
- "production exactly once" from the in-memory demo ledger;
- "automatic privacy detection" from caller-supplied metadata.

## Provider contract rule

Trust zones are operator-declared governance metadata.

Never infer a trust zone from:

- hostname;
- IP geolocation;
- provider class name;
- model name.

For the demo, the configuration explicitly declares which provider belongs to which boundary.

## Model consistency rule

Prefer Nemotron models in all three boundaries.

Current candidates:

```text
LOCAL:
  nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF Q4_K_M

EU:
  NVIDIA NIM for Nemotron 3.5 Lightning 30B A3B

GLOBAL:
  nvidia/nemotron-3.5-lightning-30b-a3b via Build.NVIDIA.com
```

Do not change vendors to make a quick test pass. If a candidate is incompatible, document the blocker and choose another NVIDIA/Nemotron candidate with rationale.

## Provider implementation rule

Use existing abstractions first:

```text
OpenAiCompatibleProvider
ModelAliasProvider
CountingModelProvider
```

A new provider implementation is justified only if the current adapter cannot correctly handle the target API contract.

Avoid contest-driven framework abstraction work unless it is required for correctness.

## EU_CLOUD implementation rule

This is critical.

If pinned TramAI lacks a regional cloud zone:

**Wrong:**

```text
Nebius provider -> mark GLOBAL_CLOUD
application if (provider == Nebius) allow EU_ONLY
```

**Correct:**

```text
extend TramAI governance model with explicit regional trust semantics
add exhaustive policy tests
then configure Nebius provider with that semantic
```

The contest claim depends on TramAI being the enforcement layer.

## PDF implementation rule

The order is security-significant:

```text
metadata parse -> policy context -> authorization -> content processing
```

not:

```text
send PDF to model -> discover label -> decide routing
```

If metadata is absent or untrusted, fail closed for the contest path.

## Real-provider test strategy

Three layers:

### Layer 1 — deterministic CI

Always green without network/secrets.

Covers:

- policy matrix;
- counters;
- structured outputs;
- approval semantics;
- duplicate rejection;
- audit chain;
- PDF metadata parsing.

### Layer 2 — opt-in provider smoke

Requires explicit environment secrets/real endpoint.

Covers:

- actual OpenAI compatibility;
- actual model ID;
- typed output compatibility;
- connectivity.

### Layer 3 — full contest rehearsal

Requires all three providers.

Covers:

- PUBLIC -> GLOBAL;
- EU_ONLY -> EU;
- LOCAL_ONLY -> LOCAL;
- forced illegal routes;
- governed payment;
- evidence output.

No layer should silently fall back from real to deterministic when the operator explicitly requested a real-provider run.

## Fail-closed configuration

If a real provider is explicitly enabled but required values are incomplete, startup/preflight should fail with a clear safe error rather than silently falling back.

Examples:

- base URL present but model missing;
- EU endpoint selected but auth token missing;
- global real mode selected but NVIDIA API key missing.

The existing deterministic mode can still be the default when no real mode is requested.

## Cost control

Nebius H200 time is expensive relative to local development.

Agents should:

- do code/unit work deterministically first;
- create/start EU endpoints only for infrastructure validation/rehearsal;
- stop/delete resources when not in use where practical;
- record necessary deployment parameters so recreation is deterministic;
- never spin up eight GPUs for a 30B model unless one-GPU compatibility has been disproven.

## Contest presentation rule

Do not optimize code for showing lots of technology names.

Every visible element must answer one of these questions:

1. What is the document allowed to do/where may it go?
2. Did an illegal provider see the request?
3. Which NVIDIA boundary processed it?
4. What action did the model propose?
5. Did the runtime authorize the side effect?
6. Can the execution be evidenced afterward?

If a feature cannot improve one of these answers before freeze, it is probably P2.

## Stop conditions

An agent should stop and report rather than improvise when:

- a proposed fix weakens an existing security invariant;
- the only path forward requires committing a secret;
- the pinned TramAI API cannot express the needed policy without an upstream change;
- a provider/model license or access requirement is unclear;
- a cloud region/GPU claim cannot be evidenced;
- a change would destabilize `main`/KTConf rather than remain contest-isolated;
- a P2 enhancement risks the engineering freeze.

## Handoff quality bar

The next agent should be able to continue without rediscovering what happened.

Every completed milestone must update either:

- `docs/gtc/CURRENT-STATE.md` (create in M0), or
- an appropriate evidence document under `docs/gtc/evidence/`.

Include exact commands and results, but sanitize secrets and private endpoint tokens.
