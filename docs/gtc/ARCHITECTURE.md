# GTC target architecture

## Architecture statement

The contest branch demonstrates one Spring Boot application with one TramAI
governance plane and three execution boundaries. LOCAL and GLOBAL_CLOUD are
NVIDIA-backed; the active EU_CLOUD implementation is temporarily Scaleway
Generative APIs serverless with Mistral Medium 3.5 128B and is not an NVIDIA/Nemotron/NIM path.

The application may choose a route. TramAI independently decides whether that route is allowed for the trusted handling metadata attached to the document.

The model is never the authority for:

- where protected data may be processed;
- whether a provider may be called;
- whether a high-risk side effect may execute;
- whether a suspended workflow may resume more than once.

## End-to-end flow

```text
                             PDF upload
                                 |
                     local metadata extraction
                       (no model invocation)
                                 |
                     classification/residency
                                 |
                                 v
                     application route choice
                                 |
                                 v
                    +-------------------------+
                    |       TramAI policy     |
                    |  provider authorization |
                    +------------+------------+
                                 |
             +-------------------+-------------------+
             |                   |                   |
             v                   v                   v
           LOCAL              EU_CLOUD          GLOBAL_CLOUD
             |                   |                   |
        NVIDIA RTX       Scaleway Europe      Build.NVIDIA.com
             |             Mistral 24B               |
        llama.cpp       Generative APIs       integrate.api.nvidia.com
             |                   |                   |
     Nemotron 3 Nano 4B       Mistral             Nemotron 3.5
             |                   |            Lightning 30B A3B
             +-------------------+-------------------+
                                 |
                         typed AI result
                                 |
                      model proposes tool
                                 |
                                 v
                    +-------------------------+
                    |    TramAI tool policy   |
                    | HIGH / HUMAN_REQUIRED   |
                    +------------+------------+
                                 |
                         approval / denial
                                 |
                           continuation
                                 |
                       exactly-once demo write
                                 |
                         hash-chain evidence
```

## Trust and residency model

### Trusted inputs

For this demo, document classification and residency are governance facts supplied through a trusted metadata mechanism.

Minimum metadata model:

```kotlin
enum class DocumentClassification {
    PUBLIC,
    CONFIDENTIAL,
    RESTRICTED,
}

enum class ResidencyRequirement {
    ANY,
    EU_ONLY,
    LOCAL_ONLY,
}
```

These names are illustrative; final domain names should align with existing TramAI types where possible.

### Policy matrix

| Classification / residency | LOCAL | EU_CLOUD | GLOBAL_CLOUD |
|---|:---:|:---:|:---:|
| PUBLIC / ANY | allow | allow | allow |
| CONFIDENTIAL / EU_ONLY | allow | allow | deny |
| RESTRICTED / LOCAL_ONLY | allow | deny | deny |

Normal preferred routes:

```text
PUBLIC       -> GLOBAL_CLOUD
CONFIDENTIAL -> EU_CLOUD
RESTRICTED   -> LOCAL
```

The contest proof deliberately overrides normal routing to show policy enforcement independent from routing correctness.

## Important upstream question: EU trust-zone semantics

The current KTConf project demonstrably uses `LOCAL` and `GLOBAL_CLOUD`. Before implementing the third provider, inspect the **pinned** TramAI revision used by this repository and determine whether it already has a regional/EU trust-zone concept.

Rules:

1. If native regional semantics exist, use them.
2. If they do not exist, implement the smallest correct upstream TramAI capability with tests.
3. Do not represent Nebius France as `GLOBAL_CLOUD` and then special-case it in application code; that would weaken the central claim that the governance runtime owns the boundary.
4. Do not introduce legal claims into the trust-zone name. `EU_CLOUD` or equivalent means an operator-declared regional execution boundary, not proof of regulatory sovereignty.

## Provider identities

Recommended contest provider identities:

```text
local-nvidia-provider
  trust zone: LOCAL
  actual endpoint: local llama.cpp
  actual model: nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF

eu-scaleway-provider
  trust zone: EU_CLOUD
  actual endpoint: Scaleway Generative APIs (European deployment)
  actual model: configured Mistral Medium 3.5 128B deployment (temporary)

global-nvidia-provider
  trust zone: GLOBAL_CLOUD
  actual endpoint: https://integrate.api.nvidia.com/v1
  actual model: nvidia/nemotron-3.5-lightning-30b-a3b
```

Keep logical model names separate from deployment model IDs. The current `ModelAliasProvider` pattern is suitable for this.

## Counting provider invariant

Every provider used in a policy-boundary proof should remain wrapped in `CountingModelProvider` or an equivalent deterministic counting seam.

The strongest visual/security evidence is:

```text
before = 7
request = forbidden
HTTP = 403
policy = classification/residency routing denied
after = 7
invocation delta = 0
```

This proves the denial occurred before model/provider invocation rather than after a response came back.

Required counters:

```text
localInvocationCount
euInvocationCount
globalInvocationCount
paymentExecutionCount
```

Counters are demo evidence, not production metrics architecture.

## Document boundary

### Required ordering

```text
receive bytes
    |
validate file type / size
    |
read trusted metadata locally
    |
construct governance context
    |
choose proposed route
    |
TramAI authorizes route
    |
ONLY NOW may document content enter provider-specific processing
```

### Fail-closed cases

Before any provider invocation, reject:

- missing classification;
- unknown classification;
- missing required residency;
- malformed metadata;
- unsupported document type;
- oversized document;
- invalid PDF if content parsing is required.

Never log raw document content as part of governance decisions.

## Document extraction scope

For P0, keep extraction intentionally small.

If PDFs contain machine-readable invoice text, parse locally with a deterministic PDF library. The selected governed operation authorizes provider egress before the extracted minimum required text is sent to the provider.

Only add NVIDIA document OCR/Parse if a real contest scenario requires image/scanned PDFs and the additional component is stable enough before freeze. The contest does not need document AI merely to increase product count.

## GLOBAL deployment

The NVIDIA hosted catalog exposes an OpenAI-compatible API at:

```text
https://integrate.api.nvidia.com/v1
```

Initial model:

```text
nvidia/nemotron-3.5-lightning-30b-a3b
```

Application configuration should use an environment-only secret such as:

```text
GTC_NVIDIA_GLOBAL_API_KEY
```

Never commit or print its value.

The existing `OpenAiCompatibleProvider.bearerToken(...)` path is expected to be sufficient unless compatibility testing proves otherwise.

## LOCAL deployment

Initial local model:

```text
nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M
```

Rationale:

- NVIDIA-developed Nemotron family;
- intentionally designed for local/RTX use;
- official GGUF exists;
- llama.cpp provides an OpenAI-compatible endpoint;
- small enough to make LOCAL easy to reproduce.

Initial serving shape:

```bash
llama serve -hf nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M \
  --host 0.0.0.0 \
  --port 8088
```

Before freeze, record:

- exact llama.cpp version/commit;
- CUDA version;
- GPU model(s);
- GGUF quantization;
- served model ID returned by `/v1/models`;
- cold/warm startup notes only if useful to demo reliability.

## EU deployment: Scaleway Generative APIs

The active EU deployment is documented in [`SCALEWAY.md`](SCALEWAY.md). It is
an OpenAI-compatible European Mistral Medium 3.5 128B service used temporarily to
unblock the EU execution-boundary integration. The provider is declared
`EU_CLOUD` by TramAI; the model/provider choice is not the trust zone itself.

The original Nebius + NVIDIA NIM investigation remains in
[`NVIDIA-NEBIUS.md`](NVIDIA-NEBIUS.md) and `CURRENT-STATE.md` as historical
evidence. It is not the active P0 route.

## Historical EU deployment: Nebius + NVIDIA

Preferred region:

```text
eu-west1 = France
```

Preferred GPU:

```text
gpu-h200-sxm
1gpu-16vcpu-200gb
NVIDIA H200, 141 GB HBM3e
```

Preferred service:

```text
Nebius Serverless AI endpoint
```

Why Serverless AI first:

- accepts arbitrary container images;
- supports NVIDIA/private registry authentication;
- supports GPU platform/preset selection;
- exposes a persistent inference endpoint;
- supports endpoint token authentication;
- keeps infrastructure work smaller than Kubernetes.

Fallback:

```text
raw Nebius Compute VM on the same EU/NVIDIA platform
```

Do not use the historical Nebius standalone NVIDIA NIM application; it was discontinued in May 2026.

## NIM registry flow on Nebius

Preferred flow:

```text
NVIDIA NGC / nvcr.io
        |
  registry credentials
        |
 Nebius MysteryBox
        |
 Serverless AI --registry-secret
        |
 NVIDIA NIM container on H200
```

A Nebius Container Registry is optional and should not be added unless one of these is true:

- we need to pin/mirror a tested NIM image digest;
- NGC pulling is unreliable in the target service;
- deployment automation becomes simpler by using a same-project image.

If mirroring becomes necessary:

```text
pull nvcr.io image locally
 -> tag cr.eu-west1.nebius.cloud/<registry-path>/...
 -> push
 -> deploy same-project image
```

## NIM model choice

Start with:

```text
nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:<tested-tag>
```

The Build.NVIDIA.com self-host instructions currently expose this model as a downloadable NIM. `latest` is acceptable only for the initial spike. Final evidence must record a pinned tag/digest.

If the model/NIM fails on one H200, do not immediately change architecture. First determine whether the failure is:

- image/runtime compatibility;
- NIM cache/storage;
- GPU architecture/profile selection;
- model-specific minimum GPU requirement;
- endpoint resource limits.

Change model only after documenting the reason.

## Authentication boundaries

### GLOBAL

```text
NVIDIA_API_KEY -> app process environment
```

### EU / registry

```text
NGC personal API key -> Nebius MysteryBox registry secret
registry username -> literal $oauthtoken when required
```

### EU endpoint

```text
Nebius endpoint auth token -> app secret/environment
```

No secret belongs in:

- application.yml defaults;
- README examples with real values;
- test fixtures;
- logs;
- audit evidence;
- screenshots/video.

## Tool authority boundary

The existing `SchedulePaymentTool` is the contest action proof.

Do not weaken its metadata:

```text
permission: payment.schedule
risk: HIGH
approval: HUMAN_REQUIRED
side effect: WRITE
managed network egress: DENY
audit: FULL
```

The desired story is not that Nemotron refuses to make payments. The desired story is that **even a valid model request for payment does not grant execution authority**.

## Claims boundary

Safe claims:

- the global NVIDIA endpoint was not invoked for a forbidden EU-only request if counter delta is zero;
- the Scaleway endpoint is not invoked for a forbidden local-only request if counter delta is zero;
- the active EU endpoint is hosted by the configured European managed service;
- the model/provider identity is reported truthfully and separately from `EU_CLOUD`;
- the model proposes; TramAI authorizes provider/tool execution.

Unsafe claims without additional evidence:

- EU AI Act compliance;
- legal sovereignty;
- zero foreign administrative access;
- automatic sensitive-data detection;
- production-grade financial exactly-once semantics;
- infrastructure network isolation merely because TramAI assigned a trust zone.

## Architecture success test

A reviewer should be able to deliberately change the application routing decision to the wrong boundary and still observe the same safety outcome because the runtime policy rejects the placement before provider invocation.

That is the architectural differentiator this contest branch exists to prove.
