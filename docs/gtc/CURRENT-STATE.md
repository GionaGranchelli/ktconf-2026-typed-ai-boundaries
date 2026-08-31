# GTC current state

Last updated: **2026-08-31**

Branch: `contest/gtc-nvidia-submission`  
Base commit: `287d52cc44ed28e7e5c2d9ddb21ac8b99504a64e` (`main` at branch creation)

## Status

The contest workstream is **M0 COMPLETE / M1 GLOBAL PROOF COMPLETE**.

Tasks 001–005 are complete. The deterministic KTConf path remains green, and
the real GLOBAL NVIDIA hosted proof is recorded below.

## Completed checkpoints

### task-001 — native regional trust semantics

The pinned TramAI revision already provides the required first-class governance capability; no submodule change was necessary:

- `dev.tramai.security.ProviderTrustZone` has `LOCAL`, `EU_CLOUD`, and `GLOBAL_CLOUD`.
- `ProviderRoutingConfiguration` supplies matrix rules and separates primary from fallback zones.
- `SovereignProfileConfiguration.toPolicyConfiguration()` enables the matrix and uses `sovereignDefaults()`.
- Defaults are `PUBLIC/INTERNAL -> LOCAL, EU_CLOUD, GLOBAL_CLOUD`; `CONFIDENTIAL -> LOCAL, EU_CLOUD`; `RESTRICTED -> LOCAL`.
- `DefaultPolicyEngine` evaluates the matrix before provider invocation and returns `classification-routing-blocked` for a disallowed route.

The exact implementation is in the clean pinned submodule at `852c89ba265466c54217557d2dc5db83760691c9`; `DefaultPolicyEngineTest` passed all 69 tests, covering the allow/deny matrix, fallback behavior, cache reauthorization, and deny-before-invocation policy path. The repository-level exhaustive matrix test also passed 1/1.

### task-002 — isolated GLOBAL NVIDIA provider

The application now has a separate `global-nvidia-provider` identity and `global-nvidia-invoice-model`. It uses `OpenAiCompatibleProvider` through `ModelAliasProvider`, remains wrapped by `CountingModelProvider`, and is deterministic unless `KTCONF_GTC_GLOBAL_NVIDIA_API_KEY` is supplied. The existing DeepSeek `cloud-provider` family is unchanged.

Opt-in proof command: `./scripts/gtc-global-nvidia-smoke` with `NVIDIA_API_KEY` set in the environment. It validates the configured model against `/models`, performs a direct `/chat/completions` smoke, then exercises the typed application endpoint and verifies restricted-route counter delta `0`.

Verification completed:

- `zsh -lc 'source ~/.zshrc && ./scripts/gtc-global-nvidia-smoke'` — model
  catalog, direct HTTP 200, typed application HTTP 200,
  `selectedRoute=GLOBAL_CLOUD`, invocation count 1, and restricted denial
  before invocation with delta 0.
- `./gradlew :app:test --no-daemon --console=plain --rerun` — BUILD SUCCESSFUL.
- `./scripts/stress-rehearse` — deterministic 20/20 rehearsal passed.
- focused TramAI `DefaultPolicyEngineTest` suite — 69 tests, 0 failures.
- Sanitized evidence: `docs/gtc/evidence/global-nvidia-smoke.md`.

`./scripts/preflight` remains blocked only by the pre-existing dirty
`vendor/tramai` working tree; the pinned submodule SHA is unchanged.

### task-003 — isolated LOCAL NVIDIA provider

The application now has a separate `local-nvidia-provider` identity and
`local-nvidia-invoice-model`, declared `LOCAL` by the governance configuration.
It uses the existing OpenAI-compatible adapter and counting seam, while the
existing Qwen/Z840 `local-provider` path remains unchanged. The opt-in typed
route is `POST /invoices/local-nvidia`, exercised by
`./scripts/gtc-local-nvidia-smoke`.

The selected artifact is NVIDIA's
`nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M`; the model repository declares
the `nvidia-nemotron-open-model-license`. The bounded startup shape is
`llama-server -hf nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M --host
0.0.0.0 --port 8088`.

Verification: provider/configuration and application tests remain green after
adding the route. The real local smoke passed with RTX 3060,
driver `580.173.02`, 12 GiB reported GPU memory, llama.cpp `9986
(91c631b21)`, model catalog entry `nvidia/nemotron-3-nano-4b`, direct HTTP
200, and typed application HTTP 200 with `selectedRoute=LOCAL_NVIDIA` and
`localNvidiaInvocationCount=1`. The local endpoint used was
`http://127.0.0.1:1234/v1`; the smoke script now defaults to that endpoint and
model while retaining environment overrides. The TramAI structured-output
schema generator was corrected to describe Kotlin enums as JSON strings with
their allowed values; its focused enum test and the local typed proof pass.

### task-004 — EU_CLOUD managed inference via Scaleway

The active application identity is now `eu-scaleway-provider` with
`eu-scaleway-invoice-model`, declared `EU_CLOUD` by TramAI configuration. It
reuses the OpenAI-compatible adapter, model alias, and counting seam, and is
deterministic unless both `KTCONF_GTC_EU_SCALEWAY_BASE_URL` and
`KTCONF_GTC_EU_SCALEWAY_API_KEY` are supplied. The opt-in typed route is
`POST /invoices/eu-scaleway`, exercised by `./scripts/gtc-eu-scaleway-smoke`.
The active temporary model is the configured Mistral Small 24B deployment;
Mistral is not NVIDIA, Nemotron, or NIM.

Scaleway setup and the replacement path are documented in
[`SCALEWAY.md`](SCALEWAY.md). The operator ran the real smoke against the
configured European deployment using model
`mistral/mistral-small-24b-instruct-2501:bf16`; the initial stale base URL
returned HTTP 404, and the corrected `/v1` base URL passed catalog validation,
direct chat, typed application HTTP 200 with `selectedRoute=EU_CLOUD`, allowed
invocation delta `1`, and forced restricted-EU HTTP 403 with invocation delta
`0`. No credential value is recorded.

Deterministic verification for the pivot passed with `./gradlew :app:test`
(`BUILD SUCCESSFUL`) and `./scripts/stress-rehearse` (`20 / 20 PASS`). The
new `CONFIDENTIAL -> /invoices/eu-scaleway` fixture path returns
`EU_CLOUD` with the EU counter incrementing once; forced
`RESTRICTED -> /invoices/boundary/restricted-eu` returns
`classification-routing-blocked` with EU counter delta `0`. The opt-in smoke
script fails closed when `SCW_API_KEY` or `SCW_MODEL` is absent; the real
Scaleway run above used both without exposing either value.

The deterministic stage scripts and `ScriptSanitizationTest` now clear all
contest real-provider variables, including generic `SCW_*` fallbacks, local
and global NVIDIA configuration, and namespaced EU configuration. This
prevents ambient shell exports from changing the offline conference path.
Sanitized real-run evidence is stored in
`docs/gtc/evidence/scaleway-smoke.md`.

The separate `task/011-gtc-governance-console` branch has not been modified.
Before it is merged, its EU presentation must use the active provider name,
model, and `euScalewayInvocationCount` (or a generic EU-provider metric), not
the historical Nebius/H200/NIM/Nemotron labels.

The following Nebius records are retained as historical evidence, not as the
active EU implementation.

Historical deployment target was Nebius `eu-west1` (France),
`gpu-h200-sxm`, and the official NIM image
`nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b`. Current NVIDIA NIM
documentation also exposes a tested-version shape using tag `2.0.9-variant`;
the final deployment must pin the tag or digest actually run. Nebius's current
endpoint CLI supports `--platform gpu-h200-sxm` and MysteryBox-backed
`--registry-secret`.

Verification: provider/configuration and Spring context tests pass after the
EU provider is added. Nebius CLI authentication is now configured through the
federated profile, and the existing default `eu-west1` project is usable. Tenant
project creation was denied by IAM, so no new project was attempted after the
existing project was discovered.

Capacity evidence: `eu-west1` reports fresh medium on-demand availability for
`gpu-h200-sxm` / `1gpu-16vcpu-200gb`.

Deployment evidence: the first endpoint attempt used `:latest` and failed with
Nebius operation `code=13` internal error; it was removed. A replacement
endpoint using the pinned image
`nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:2.0.9-variant`, both NGC
SecretStash selectors, `NIM_MODEL_NAME`, `NIM_SERVED_MODEL_NAME`,
`NIM_PASSTHROUGH_ARGS=--reasoning-parser nemotron_v3`, H200 single-GPU preset,
250 GiB disk, and 16 GiB shared memory was also unsuccessful.
At the latest checkpoint the replacement state is still `STARTING` after a
restart requested by the operator. The original create operation remains
failed with Nebius `code=13` (`Operation failed with internal error`); the
restart operation remained unfinished, and no
successful inference has been observed. Direct NIM inference and the typed
`EU_CLOUD` application proof remain pending.

After NGC key rotation, inspection showed the existing endpoint still pinned
to the previous registry secret version. A clean replacement was initiated;
the delete operation was left in progress, so the
replacement endpoint has not yet been created.

Subsequent Nebius attempts created two H200 endpoints, but both failed before
workload startup with compute `code=8` (`NotEnoughResources`): the requested
`1gpu-16vcpu-200gb` VM reached its scheduling timeout. This is a capacity
failure, not evidence of an invalid NGC key or NIM image. No direct EU NIM
inference has succeeded.

A separate capacity diagnostic endpoint was then created with the same `eu-west1`
H200 and `1gpu-16vcpu-200gb` request, but the smaller official image
`nvcr.io/nim/nvidia/nemotron-3-nano:1.7.0-variant`. At the latest checkpoint
it reached `Workload initialization started` and then failed with Nebius
operation `code=13` (`Operation failed with internal error`); no inference
result is available.

For a regional comparison, a diagnostic endpoint was created in the tenant's
`eu-north1`
project with the same small Nemotron 3 Nano NIM image and 1-GPU preset, using
the region's H100 platform. It reached `Workload initialization started` and
then failed with Nebius operation `code=13` (`Operation failed with internal
error`); no inference result is available.

The paired diagnostics show two distinct failures: the original large-model
attempts were rejected with compute `code=8` due to capacity, while the small
model on both H200 and H100 passed initial scheduling and failed during
workload initialization with `code=13`. Model size alone is therefore
unlikely to explain the failures; NIM runtime compatibility, secret-backed
image access, or a Nebius Serverless AI issue remain open.

### task-005 — real synthetic PDF + trusted metadata

The application now exposes `POST /invoices/analyze-pdf` as a multipart upload
with a 5 MiB limit. `TrustedPdfIngestionService` uses PDFBox locally, requires
the embedded `KTCONF-Classification` and `KTCONF-Residency` properties, fails
closed on missing/unknown/contradictory metadata, and extracts only the
synthetic `key=value` invoice fields after metadata validation. The metadata
contract is documented in `docs/gtc/PDF-METADATA-CONTRACT.md`.

Three repository fixtures cover PUBLIC/ANY, CONFIDENTIAL/EU_ONLY, and
RESTRICTED/LOCAL_ONLY; they contain synthetic contest data only. The parser
test also covers missing classification, contradictory restricted residency,
non-PDF input, and fixture ingestion.

Task-005 is now complete. The PDF service separates trusted metadata reading
from invoice content extraction, and the application suite includes a
multipart malformed-PDF rejection proof with HTTP 400 and unchanged counters
for every provider. TramAI's governed operation remains the provider
authorization boundary; task-006 must use the metadata phase to choose the
boundary before extracted content is sent to a provider.

### task-006 — governed three-boundary integration (REVIEW)

Task-006 remains in review. The PDF endpoint now derives the proposed execution
boundary from trusted residency metadata: `ANY` selects `GLOBAL_CLOUD`,
`EU_ONLY` selects `EU_CLOUD`, and `LOCAL_ONLY` selects `LOCAL_NVIDIA`.
The selected operation still passes through TramAI's classification routing
matrix; no provider or vendor is authorized in application code. The trusted
metadata contract is fail-closed to PUBLIC/INTERNAL + ANY, CONFIDENTIAL +
EU_ONLY, and RESTRICTED + LOCAL_ONLY. A dedicated confidential-EU forced-global
multipart proof returns 403 with global invocation delta `0`, and the same PDF
succeeds on EU. Individual real provider proofs exist, but a combined real PDF
run across all three providers is still pending.

Verification: `./gradlew :app:test --tests
dev.giona.ktconf.TrustedPdfIngestionServiceTest --no-daemon --console=plain
--rerun` passed 6/6. The focused Spring context test also passed. Metadata
rejection occurs before `InvoiceService` is called, so no provider operation is
entered; the existing route-denial integration test remains the explicit
provider-counter proof for TramAI's deny-before-invocation behavior.

### task-007 — governed payment on NVIDIA path

Task-007 implementation is complete and in review. An explicit
`/invoices/analyze/local-nvidia` route uses the local NVIDIA typed operation
with the existing `schedule-payment` tool metadata, then TramAI suspends the
high-value action for approval. Deterministic tests prove payment count 0 at
suspension, 0 -> 1 after approval, duplicate rejection, denial, and valid
audit evidence. The real payment smoke is not yet claimed: the local endpoint
at `127.0.0.1:1234` was unreachable during verification.

The assessment/tool prompt now states the amount-based risk/action rules and
the successful-tool-result transition explicitly. The canonical
`payment-local-invoice.pdf` denial test proves `202` suspension, payment count
`0`, denial, and subsequent resume rejection with `409`. The full deterministic
suite and 20/20 rehearsal pass. Real Nemotron payment and audit evidence remain
pending local endpoint availability.

## Locked contest direction

Working title:

> **The Model Is Not the Authority**

Technical tagline:

> **One Spring application. One TramAI policy plane. Three governed execution boundaries.**

Target routes:

```text
LOCAL
  -> NVIDIA RTX
  -> llama.cpp
  -> NVIDIA Nemotron 3 Nano 4B

EU_CLOUD
  -> Scaleway Generative APIs Europe
  -> Mistral Small 24B (temporary)

GLOBAL_CLOUD
  -> Build.NVIDIA.com hosted API
  -> NVIDIA Nemotron 3.5 Lightning 30B A3B
```

Input evolution:

```text
JSON-only invoice
    -> real synthetic PDF
    -> trusted classification/residency metadata read locally
    -> proposed route
    -> TramAI placement authorization
    -> allowed configured provider only
```

Existing payment/approval/evidence mechanics are to be reused rather than rebuilt.

## Existing baseline strengths to preserve

The base application already has:

- deterministic local/cloud providers for offline rehearsal;
- optional real local and cloud providers;
- `CountingModelProvider` proof seam;
- deliberate restricted-to-cloud misroute denied before provider invocation;
- structured output validation;
- HIGH-risk `schedule-payment` tool with `HUMAN_REQUIRED`;
- suspension and continuation;
- duplicate approval/resume rejection;
- demo-scoped exactly-once ledger behavior;
- hash-chained audit evidence;
- explicit claims-boundary documentation.

## Critical unknowns / first discoveries required

### 1. TramAI regional trust zone

We know the pinned application uses `LOCAL` and `GLOBAL_CLOUD`.

**Unresolved:** whether the pinned TramAI revision already exposes a native regional / `EU_CLOUD` trust-zone semantic.

Required action:

- inspect the pinned submodule source/API;
- if absent, add the smallest correct upstream governance capability with exhaustive routing tests;
- never special-case Nebius as EU-only in application code while telling the contest story that TramAI owns placement authorization.

### 2. GLOBAL NVIDIA API

The user has Build.NVIDIA.com access and an NVIDIA API key.

First target:

```text
base URL: https://integrate.api.nvidia.com/v1
model:    nvidia/nemotron-3.5-lightning-30b-a3b
```

Required action:

- direct smoke with the user's key;
- then integrate through existing OpenAI-compatible provider abstractions;
- preserve invocation counting.

### 3. NGC credentials for Nebius NIM

A Build.NVIDIA.com hosted-inference API key should **not be assumed** to be the same credential needed for NGC registry/model access.

Required action:

- check whether the selected public NIM image can be pulled keylessly;
- if credentials/model-download access are required, create/use an NVIDIA NGC Personal API key with the required Catalog permission;
- store registry credentials in Nebius MysteryBox, never git.

### 4. Historical Nebius H200 quota/capacity

Preferred EU target:

```text
region:   eu-west1 (France)
platform: gpu-h200-sxm
preset:   1gpu-16vcpu-200gb
```

Required action:

- verify project/region;
- check quota/capacity now;
- request quota immediately if unavailable because cloud-access lead time is the main schedule risk.

### 5. Historical Nebius registry strategy

Initial conclusion:

> **Do not create a Nebius Container Registry merely to fetch NVIDIA NIM.**

Preferred first path:

```text
nvcr.io
 -> Nebius MysteryBox registry secret if needed
 -> Nebius Serverless AI --registry-secret
 -> NIM on NVIDIA H200
```

Use/mirror into Nebius Container Registry only if direct NGC pull is unreliable, incompatible, or we need a controlled immutable mirror.

### 6. Local RTX host

First model candidate:

```text
nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M
```

Required action:

- verify download/serve on the selected NVIDIA RTX host with llama.cpp;
- record exact model/quant/runtime/GPU/CUDA evidence.

## Immediate parallel work

After baseline verification, the following can proceed independently:

| Track | Owner scope | First success condition |
|---|---|---|
| A | TramAI trust-zone semantics | full three-boundary policy matrix expressible/tested |
| B | GLOBAL NVIDIA | hosted Nemotron returns typed-compatible response |
| C | LOCAL NVIDIA | RTX llama.cpp Nemotron endpoint answers OpenAI request |
| D | Scaleway/Mistral | European Generative APIs deployment serves typed-compatible inference |
| E | PDF metadata | synthetic PDF is parsed locally and fails closed without trusted label |

Integration should wait until the provider endpoints and policy semantics are independently proven.

## P0 submission proof target

The contest branch must eventually demonstrate:

```text
PUBLIC -> GLOBAL NVIDIA -> success
CONFIDENTIAL + EU_ONLY -> Scaleway Europe / Mistral -> typed success
LOCAL_ONLY -> local NVIDIA RTX -> success

EU_ONLY forced -> GLOBAL -> denied, global invocation delta 0
LOCAL_ONLY forced -> EU -> denied, EU invocation delta 0

Nemotron -> schedule-payment(EUR 18,400)
TramAI -> HUMAN_REQUIRED
payment count -> 0
approve -> 1
duplicate approve -> rejected, still 1
audit chain -> VALID
```

## Current repository delta

At this checkpoint, contest branch changes are planning/doc files plus a README banner. Implementation milestones remain unchecked until agents produce test evidence.

## Next recommended command/task for an agent

Start M0, not provider coding:

1. checkout `contest/gtc-nvidia-submission`;
2. initialize pinned TramAI submodule;
3. run baseline deterministic gates;
4. inspect the pinned provider trust-zone enum/routing matrix;
5. report whether `EU_CLOUD` exists and what exact upstream change is required if it does not.

Then Tracks B-D/E can proceed in parallel.
