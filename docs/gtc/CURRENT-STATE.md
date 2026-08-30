# GTC current state

Last updated: **2026-08-30**

Branch: `contest/gtc-nvidia-submission`  
Base commit: `287d52cc44ed28e7e5c2d9ddb21ac8b99504a64e` (`main` at branch creation)

## Status

The contest workstream is **PLANNED / NOT YET IMPLEMENTED**.

The branch currently contains planning and agent-execution documentation only. No contest production behavior has been added yet, so the KTConf implementation remains identical to the base commit except for documentation/discoverability files.

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
