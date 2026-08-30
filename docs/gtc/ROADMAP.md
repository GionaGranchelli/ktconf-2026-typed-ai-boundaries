# GTC 2026 execution roadmap

This roadmap is for `contest/gtc-nvidia-submission` only.

Engineering freeze target: **2026-09-08**  
Contest deadline: **2026-09-10**

The work is intentionally milestone-gated. Do not start visual polish before the real NVIDIA path and policy proofs work.

## Priority model

- **P0**: required for a credible winning submission.
- **P1**: materially raises judging quality; do after P0 is stable.
- **P2**: optional polish; must never threaten P0/P1.

## Milestone dependency graph

```text
M0 baseline
 |
 +--> M1 GLOBAL NVIDIA -------------------+
 |                                        |
 +--> M2 LOCAL NVIDIA --------------------+--> M5 three-boundary governance
 |                                        |            |
 +--> M3 EU NVIDIA / Nebius --------------+            +--> M6 governed payment
 |                                                     |
 +--> M4 real PDF + trusted metadata ------------------+
                                                       |
                                                       v
                                               M7 evidence/benchmark
                                                       |
                                                       v
                                               M8 submission package
```

M1, M2, M3 and M4 can be worked in parallel after M0.

---

# M0 — Freeze the baseline and identify upstream gaps

**Priority:** P0  
**Goal:** Prove the contest branch starts from a known-good KTConf baseline and identify any TramAI capability missing for a third regional trust boundary.

## Tasks

- [ ] Record branch base commit and pinned TramAI submodule commit in `docs/gtc/CURRENT-STATE.md`.
- [ ] Run the current deterministic gates unchanged:
  - [ ] `./scripts/preflight`
  - [ ] `./scripts/stress-rehearse`
  - [ ] full Gradle test suite
- [ ] Confirm current two-zone policy behavior remains green.
- [ ] Inspect the pinned TramAI API for provider trust-zone support.
- [ ] Determine whether a native `EU_CLOUD` / regional cloud trust zone already exists.
- [ ] If not, create a narrow upstream TramAI work item/branch to add the minimum correct regional-zone semantics. Do **not** fake EU semantics by mapping `EU_CLOUD` to `GLOBAL_CLOUD` in the demo.
- [ ] Decide the classification/residency policy matrix and lock it in tests before provider work depends on it.

## Acceptance criteria

- Existing KTConf oracle is green on the contest branch.
- The exact trust-zone capability is known, documented and tested.
- No production behavior change is introduced merely to make the roadmap compile.

---

# M1 — GLOBAL NVIDIA hosted Nemotron

**Priority:** P0  
**Goal:** Replace the contest global real-provider path with NVIDIA's hosted OpenAI-compatible API while keeping deterministic fallback untouched.

## Target

- Base URL: `https://integrate.api.nvidia.com/v1`
- First model candidate: `nvidia/nemotron-3.5-lightning-30b-a3b`
- Credential: `NVIDIA_API_KEY` via environment only
- Integration style: existing `OpenAiCompatibleProvider` + `ModelAliasProvider` unless a first-class TramAI NVIDIA provider already exists and is demonstrably better.

## Tasks

- [ ] Add a contest/global NVIDIA endpoint configuration family; do not reuse DeepSeek-specific naming.
- [ ] Add a `global-nvidia-provider` bean or rename the contest provider identity cleanly.
- [ ] Preserve `CountingModelProvider` around the real provider.
- [ ] Map the logical model name to the actual NVIDIA model ID.
- [ ] Add deterministic tests proving the global provider remains scripted without a secret.
- [ ] Add a real-provider smoke script guarded by `NVIDIA_API_KEY`.
- [ ] Prove a PUBLIC document/request returns the expected typed `InvoiceAssessment` using the real NVIDIA endpoint.
- [ ] Prove a forbidden route is denied before the NVIDIA provider counter increments.
- [ ] Ensure logs never print the API key or Authorization header.

## Acceptance criteria

```text
PUBLIC -> global-nvidia-provider -> real Nemotron -> 200 typed
EU_ONLY forced -> global-nvidia-provider -> 403
provider invocation delta for denied request = 0
```

The normal deterministic stage must still run with no NVIDIA key.

---

# M2 — LOCAL NVIDIA RTX + Nemotron

**Priority:** P0  
**Goal:** Make the LOCAL boundary visibly NVIDIA as well.

## Target

First candidate:

- Model: `nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF`
- Quantization: start with `Q4_K_M`
- Runtime: current llama.cpp with CUDA
- Endpoint: OpenAI-compatible `llama-server`
- Hardware: local NVIDIA RTX system

Recommended starting command for the inference host:

```bash
llama serve -hf nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M \
  --host 0.0.0.0 \
  --port 8088
```

Tune only after correctness is proven.

## Tasks

- [ ] Verify the model downloads and serves successfully on the intended RTX host.
- [ ] Record llama.cpp commit/version and CUDA/device information.
- [ ] Verify `/v1/models` and `/v1/chat/completions` compatibility.
- [ ] Point the contest local provider at the server through the existing OpenAI-compatible provider path.
- [ ] Ensure logical model aliases prevent infrastructure model IDs leaking into governance configuration.
- [ ] Prove a `RESTRICTED / LOCAL_ONLY` request returns a typed result.
- [ ] Add a local real-provider preflight command/script.
- [ ] Document network exposure. Prefer loopback/Tailscale/private LAN over public exposure.

## Acceptance criteria

```text
RESTRICTED / LOCAL_ONLY -> local NVIDIA Nemotron -> 200 typed
```

No cloud secret is required for this proof.

---

# M3 — EU_CLOUD on Nebius + NVIDIA H200 + NIM

**Priority:** P0  
**Goal:** Create a real EU execution boundary that is unmistakably NVIDIA-powered.

## Locked provider decision

- Cloud partner: **Nebius AI Cloud**
- Preferred region: **`eu-west1` (France)**
- GPU: **NVIDIA H200**, single-GPU preset if capacity/quota permits
- Deployment mechanism: **Nebius Serverless AI endpoint** first; raw VM fallback
- Model serving: official **NVIDIA NIM** image from `nvcr.io`
- Preferred Nemotron model: same family as GLOBAL where possible; start with `nemotron-3.5-lightning-30b-a3b`

Nebius' historical one-click NVIDIA NIM standalone application is discontinued. Do not depend on it.

## Phase A — account/capacity spike

- [ ] Confirm the user's Nebius project/region.
- [ ] Create or select an `eu-west1` project if available.
- [ ] Check H200 quota and current capacity with Nebius tooling/console.
- [ ] If H200 quota is zero, request quota immediately; do not wait until implementation is complete.
- [ ] Confirm a public Serverless AI endpoint can use `gpu-h200-sxm` + `1gpu-16vcpu-200gb` in this project.

## Phase B — NGC/NIM registry authentication

Preferred path: pull `nvcr.io` directly from Nebius Serverless AI.

- [ ] Determine whether the chosen NIM image is keyless. If not, use an NVIDIA NGC Personal API key with NGC Catalog permission.
- [ ] Never commit the key.
- [ ] Store registry credentials in Nebius MysteryBox as a registry secret.
- [ ] NVIDIA registry username is the literal `$oauthtoken` when API-key authentication is required.
- [ ] Use the Nebius endpoint's `--registry-secret` support rather than putting credentials on a command line or in source.

Nebius Container Registry is optional. Use it only if mirroring/pinning the NVIDIA image improves reliability. It is **not required** merely to pull an NGC image.

## Phase C — deploy NIM

Starting NIM image candidate:

```text
nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:latest
```

Before final freeze, replace `latest` with a tested immutable tag or digest.

Create an authenticated Nebius endpoint exposing NIM's HTTP port. Exact command parameters must be derived from the current NIM container contract and verified against current Nebius CLI output.

Conceptual command shape:

```bash
nebius ai endpoint create \
  --name gtc-eu-nvidia-nim \
  --image nvcr.io/nim/nvidia/nemotron-3.5-lightning-30b-a3b:<PINNED_TAG> \
  --registry-secret <MYSTERYBOX_REGISTRY_SECRET> \
  --platform gpu-h200-sxm \
  --preset 1gpu-16vcpu-200gb \
  --public \
  --container-port 8000 \
  --auth token \
  --token-secret <MYSTERYBOX_ENDPOINT_TOKEN_SECRET> \
  --shm-size 16Gi
```

Do not blindly copy this shape into automation: verify NIM startup needs, cache volume needs, environment variables and container port first.

## Phase D — application integration

- [ ] Add `eu-nvidia-provider` configuration.
- [ ] Wrap with `CountingModelProvider`.
- [ ] Add the provider to the sovereign allowlist and model mapping.
- [ ] Assign the correct regional trust zone.
- [ ] Prove `CONFIDENTIAL / EU_ONLY -> EU_CLOUD -> Nebius NIM -> typed 200`.
- [ ] Force `LOCAL_ONLY -> EU_CLOUD` and prove denial before counter increment.
- [ ] Record region (`eu-west1`), GPU type (H200), actual NIM image digest/tag and model ID as contest evidence.

## Acceptance criteria

```text
EU_ONLY -> Nebius France -> NVIDIA H200 -> NVIDIA NIM -> Nemotron -> 200 typed
LOCAL_ONLY forced -> EU_CLOUD -> 403
EU provider invocation delta = 0 for denied request
```

---

# M4 — Real PDF + trusted privacy metadata

**Priority:** P0  
**Goal:** Replace the visually weak JSON-only contest input with an actual document without weakening the security boundary.

## Security rule

Metadata/classification must be obtained locally before document payload is sent to any inference provider.

## First implementation

Prefer a small documented metadata contract rather than a large enterprise-label integration.

Possible implementation options, in preference order:

1. PDF XMP/custom metadata embedded in the demo PDF.
2. Trusted signed/controlled sidecar envelope submitted with the file.
3. A local-only deterministic metadata service.

Do not claim Microsoft Purview compatibility unless actual Purview labels are parsed correctly.

## Tasks

- [ ] Add multipart/PDF upload endpoint or equivalent contest-specific document API.
- [ ] Set strict upload size/type limits.
- [ ] Parse only metadata required for policy before inference.
- [ ] Reject missing/unknown classification fail-closed.
- [ ] Reject malformed/unsupported PDF fail-closed.
- [ ] Map metadata to the existing typed domain model.
- [ ] Keep raw PDF bytes out of logs and audit records.
- [ ] Add three safe synthetic PDFs:
  - [ ] `invoice-public.pdf`
  - [ ] `invoice-eu-confidential.pdf`
  - [ ] `invoice-restricted.pdf`
- [ ] Make the documents obviously synthetic and safe to publish.
- [ ] Add tests proving metadata is read before provider invocation.
- [ ] Add a deliberate wrong-route endpoint/path for the contest proof.

## Acceptance criteria

A judge can upload/drop a PDF and see its trusted handling label and resulting boundary decision without any remote provider receiving bytes before authorization.

---

# M5 — Three-boundary governance matrix

**Priority:** P0  
**Goal:** Make the three-placement policy a real runtime contract, not presentation text.

## Required matrix

```text
PUBLIC / ANY
  LOCAL        allowed
  EU_CLOUD     allowed
  GLOBAL_CLOUD allowed

CONFIDENTIAL / EU_ONLY
  LOCAL        allowed
  EU_CLOUD     allowed
  GLOBAL_CLOUD denied

RESTRICTED / LOCAL_ONLY
  LOCAL        allowed
  EU_CLOUD     denied
  GLOBAL_CLOUD denied
```

## Tasks

- [ ] Add/extend the trust-zone vocabulary correctly in TramAI if required.
- [ ] Add exhaustive policy tests for every classification x boundary combination.
- [ ] Add application routing for normal preferred route:
  - PUBLIC -> GLOBAL
  - CONFIDENTIAL/EU_ONLY -> EU
  - RESTRICTED/LOCAL_ONLY -> LOCAL
- [ ] Preserve explicit forced-route test seams to prove TramAI, not the `when`, is the authority.
- [ ] Expose counters for all three providers.
- [ ] Add a compact governance decision response/view showing:
  - classification;
  - residency;
  - selected route;
  - selected provider;
  - trust zone;
  - allow/deny result;
  - safe reason code;
  - provider invocation delta for proof scenarios.

## Acceptance criteria

Every matrix cell is tested and the forbidden cells deny before provider invocation.

---

# M6 — Governed consequential action with real Nemotron

**Priority:** P0  
**Goal:** Reuse the existing payment governance to prove model output is not authorization.

## Tasks

- [ ] Run the existing payment/tool-capable path with a real Nemotron provider.
- [ ] Ensure the model can propose/invoke `schedule-payment` through the existing tool contract.
- [ ] Preserve tool metadata:
  - permission `payment.schedule`;
  - risk `HIGH`;
  - approval `HUMAN_REQUIRED`;
  - side effect `WRITE`;
  - managed network egress denied;
  - full audit.
- [ ] Use a synthetic amount such as EUR 18,400 to make the approval consequence obvious.
- [ ] Prove payment count remains zero at suspension.
- [ ] Approve -> resume -> exactly one ledger write.
- [ ] Duplicate approve/resume -> rejected, ledger remains one.
- [ ] Deny -> ledger remains zero.

## Acceptance criteria

The video can truthfully show:

> Nemotron proposed the payment. TramAI still refused to execute it without human authorization.

---

# M7 — Evidence, adversarial cases and scoring proof

**Priority:** P1  
**Goal:** Turn architecture claims into measurable evidence.

## Required evidence

- [ ] Global wrong-route invocation delta `0`.
- [ ] EU wrong-route invocation delta `0`.
- [ ] Payment count before/after approval.
- [ ] Duplicate approval result.
- [ ] Valid hash chain.
- [ ] Provider/model/boundary metadata in an auditor-safe evidence pack.

## Mini benchmark

Create a small deterministic governance matrix rather than a fake LLM benchmark.

Recommended 30-50 scenario corpus covering:

- route/classification combinations;
- missing/malformed labels;
- attempts to force a less-trusted route;
- tool requests below/above approval threshold;
- duplicate continuation;
- denied continuation;
- invalid structured model output.

Report at minimum:

```text
policy scenarios:                 N
forbidden provider invocations:   0
forbidden tool executions:        0
duplicate payment executions:     0
audit-chain verification failures:0
```

Optional P2: add prompt-injection documents, but do not make system safety depend on the model following or refusing the injection.

---

# M8 — Contest package and freeze

**Priority:** P0/P1  
**Goal:** Make the project understandable and verifiable by a judge in minutes.

## Repository package

- [ ] Add `scripts/gtc-preflight`.
- [ ] Add `scripts/gtc-demo` with stable, short commands.
- [ ] Add an architecture diagram suitable for README/video.
- [ ] Add `docs/gtc/CLAIMS-BOUNDARY.md` if contest claims diverge from the KTConf claims file.
- [ ] Pin all model/container/runtime versions used in evidence.
- [ ] Record final real-provider evidence without secrets.
- [ ] Run a fresh clone rehearsal.
- [ ] Run demo at least 10 consecutive times with no manual repair.

## 60-second video target

```text
0-08  Upload real PDF; show trusted label.
08-18 App proposes illegal global route; TramAI denies; provider delta 0.
18-30 Same EU-only document succeeds on Nebius France / NVIDIA H200 / NIM.
30-39 Restricted PDF -> LOCAL NVIDIA only.
39-51 Nemotron proposes EUR 18,400 payment; TramAI pauses for human approval.
51-56 Approve; execution count 0 -> 1; duplicate stays 1.
56-60 Audit chain VALID + tagline.
```

## Freeze gates

- [ ] P0 tests green.
- [ ] deterministic KTConf path still green.
- [ ] real GLOBAL proof green.
- [ ] real EU proof green.
- [ ] real LOCAL proof green.
- [ ] no secrets in repository/history.
- [ ] README starts with the contest story, not framework internals.
- [ ] video is screen proof, not slides.
- [ ] public post and repository links tested while logged out.

---

# Explicit non-goals until P0 is complete

Do not spend contest-critical time on:

- fine-tuning Nemotron;
- RAG/vector databases;
- elaborate frontend design;
- multi-agent orchestration;
- automatic DLP inference;
- OCR/document AI unless plain PDF text extraction proves insufficient;
- Kubernetes unless Serverless AI/VM deployment cannot satisfy the EU proof;
- generalized provider-selection redesign beyond what the three-zone policy requires;
- production-grade financial execution.

The winning principle is **visible governance decisions, not maximum feature count**.
