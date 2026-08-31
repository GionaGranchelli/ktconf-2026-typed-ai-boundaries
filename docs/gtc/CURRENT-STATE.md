# GTC current state

Last updated: **2026-08-31**

Branch: `contest/gtc-nvidia-submission`  
Base commit: `287d52cc44ed28e7e5c2d9ddb21ac8b99504a64e` (`main` at branch creation)

## Status

The contest workstream is **M0 COMPLETE / M1 REVIEW-PENDING REAL CREDENTIAL**.

Task 001 and the opt-in portion of task 002 are implemented. The deterministic KTConf path remains green. A real NVIDIA hosted smoke is not claimed in this environment because `NVIDIA_API_KEY` was absent.

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

- `./scripts/preflight` — deterministic build/tests (63 tests, 0 failures) and boot artifact passed after the route/test corrections.
- `./scripts/stress-rehearse` — deterministic 20/20 rehearsal passed.
- focused TramAI `DefaultPolicyEngineTest` suite — 69 tests, 0 failures.
- `NVIDIA_API_KEY` — absent; direct hosted inference and the real typed application proof remain pending.

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

### task-004 — isolated EU NVIDIA/NIM provider

The application now has a separate `eu-nvidia-provider` identity and
`eu-nvidia-invoice-model`, declared `EU_CLOUD` by TramAI configuration. It uses
the existing OpenAI-compatible adapter and counter seam, and remains
deterministic unless both `KTCONF_GTC_EU_NVIDIA_BASE_URL` and
`KTCONF_GTC_EU_NVIDIA_API_KEY` are supplied. The opt-in typed route is
`POST /invoices/eu-nvidia`, exercised by `./scripts/gtc-eu-nvidia-smoke` with
`CONFIDENTIAL` input.

Current deployment target remains Nebius `eu-west1` (France),
`gpu-h200-sxm`, and the official NIM image
`nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b`. Current NVIDIA NIM
documentation also exposes a tested-version shape using tag `2.0.9-variant`;
the final deployment must pin the tag or digest actually run. Nebius's current
endpoint CLI supports `--platform gpu-h200-sxm` and MysteryBox-backed
`--registry-secret`.

Verification: provider/configuration and Spring context tests pass after the
EU provider is added. Nebius CLI authentication is now configured through the
federated profile, and the existing active project
`project-e01wv8bkpa003ccxnn0pq5` (`default-project-eu-west1`) is usable. Tenant
project creation was denied by IAM, so no new project was attempted after the
existing project was discovered.

Capacity evidence: `eu-west1` reports fresh medium on-demand availability for
`gpu-h200-sxm` / `1gpu-16vcpu-200gb`.

Deployment evidence: endpoint `aiendpoint-e01c7e05rge6v9n60e` named
`gtc-eu-nvidia-nim` was created with the direct NIM image
`nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:latest`, the two
SecretStash selectors, H200 single-GPU preset, 250 GiB disk, and 16 GiB shared
memory. Its managed URL is
`https://port8000-mtyhhsjjnp93s20.tunnel.applications.eu-west1.nebius.cloud`.
At the latest checkpoint the endpoint state is `STARTING`, its log contains
only `Workload initialization started`, and unauthenticated URL probing returns
404 while the workload is not ready. Direct NIM inference and the typed
`EU_CLOUD` application proof remain pending.

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

Verification: `./gradlew :app:test --tests
dev.giona.ktconf.TrustedPdfIngestionServiceTest --no-daemon --console=plain
--rerun` passed 5/5. The focused Spring context test also passed. Metadata
rejection occurs before `InvoiceService` is called, so no provider operation is
entered; the existing route-denial integration test remains the explicit
provider-counter proof for TramAI's deny-before-invocation behavior. A full
multipart endpoint counter test is still a follow-on hardening item.

## Locked contest direction

Working title:

> **The Model Is Not the Authority**

Technical tagline:

> **One Spring application. One TramAI policy plane. Three NVIDIA execution boundaries.**

Target routes:

```text
LOCAL
  -> NVIDIA RTX
  -> llama.cpp
  -> NVIDIA Nemotron 3 Nano 4B

EU_CLOUD
  -> Nebius AI Cloud eu-west1 (France)
  -> NVIDIA H200
  -> NVIDIA NIM
  -> NVIDIA Nemotron

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
    -> allowed NVIDIA provider only
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

### 4. Nebius H200 quota/capacity

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

### 5. Nebius registry strategy

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
| D | Nebius/NIM | France H200 endpoint serves NIM/Nemotron |
| E | PDF metadata | synthetic PDF is parsed locally and fails closed without trusted label |

Integration should wait until the provider endpoints and policy semantics are independently proven.

## P0 submission proof target

The contest branch must eventually demonstrate:

```text
PUBLIC -> GLOBAL NVIDIA -> success
EU_ONLY -> Nebius France / NVIDIA H200 / NIM -> success
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
