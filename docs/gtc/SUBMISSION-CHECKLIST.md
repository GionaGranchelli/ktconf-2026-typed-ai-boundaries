# GTC submission checklist

Use this as the final gate. A checked box should correspond to evidence, not intention.

## A. Contest fit

- [ ] Project remains public and open source.
- [ ] Submission is made as an individual developer entry.
- [ ] Final public post follows current contest rules/tags/mentions.
- [ ] Final submission is published before **2026-09-10**.
- [ ] Repository and video links work while logged out/incognito.

## B. One-line problem

A zero-context reviewer can understand this sentence:

> **AI models may reason and propose actions, but deterministic policy should decide where protected data may be processed and which consequential actions may execute.**

- [ ] README opens with the problem before implementation detail.
- [ ] Video establishes the problem in <= 8 seconds.

## C. NVIDIA technology proof

### GLOBAL

- [ ] Uses a real Build.NVIDIA.com hosted endpoint.
- [ ] NVIDIA API base URL recorded safely.
- [ ] Exact NVIDIA/Nemotron model ID recorded.
- [ ] API key is absent from git/logs/video.
- [ ] PUBLIC -> GLOBAL real smoke is green.

### EU_CLOUD

- [ ] Nebius EU region is recorded.
- [ ] Region evidence shows an EU location.
- [ ] Actual GPU platform recorded as NVIDIA hardware.
- [ ] Exact NIM image tag/digest recorded.
- [ ] Exact model ID recorded.
- [ ] Endpoint auth enabled.
- [ ] Registry/NGC secret absent from git/logs/video.
- [ ] EU_ONLY -> EU real smoke is green.

### LOCAL

- [ ] Local device is NVIDIA RTX/GPU.
- [ ] Exact CUDA/llama.cpp version recorded.
- [ ] Exact Nemotron GGUF/model ID recorded.
- [ ] LOCAL_ONLY -> LOCAL real smoke is green.

## D. Document proof

- [ ] Contest flow accepts an actual PDF.
- [ ] Fixture is synthetic and safe to publish.
- [ ] Trusted classification/residency metadata exists before inference.
- [ ] Metadata is parsed locally.
- [ ] Missing metadata fails closed.
- [ ] Malformed metadata fails closed.
- [ ] Raw PDF content is not logged by governance instrumentation.

Synthetic fixtures available:

- [ ] PUBLIC / ANY
- [ ] CONFIDENTIAL / EU_ONLY
- [ ] RESTRICTED / LOCAL_ONLY

## E. Runtime placement governance

- [ ] Three trust boundaries are represented in TramAI policy, not only app code.
- [ ] Complete classification/residency x trust-zone matrix is tested.
- [ ] Normal route: PUBLIC -> GLOBAL.
- [ ] Normal route: EU_ONLY -> EU.
- [ ] Normal route: LOCAL_ONLY -> LOCAL.

### Forced error proofs

- [ ] EU_ONLY forced to GLOBAL -> denied.
- [ ] GLOBAL invocation delta for denied request = `0`.
- [ ] LOCAL_ONLY forced to EU -> denied.
- [ ] EU invocation delta for denied request = `0`.
- [ ] Reason codes are deterministic/safe.

## F. Model vs authority proof

- [ ] Real Nemotron can produce/propose the payment action in the contest path.
- [ ] `schedule-payment` remains `HIGH` risk.
- [ ] `HUMAN_REQUIRED` remains enforced by runtime.
- [ ] At suspension, payment count = `0`.
- [ ] Approve once -> count `1`.
- [ ] Approve again -> rejected; count remains `1`.
- [ ] Deny -> payment count remains `0`.

## G. Evidence

- [ ] Audit events generated from real runtime records.
- [ ] Audit chain verifies.
- [ ] Evidence pack contains no secrets/prompts/raw protected document content.
- [ ] Provider identities are visible in evidence.
- [ ] Trust zones are visible in evidence.
- [ ] Model IDs are visible where safe.
- [ ] Region/GPU/NIM deployment evidence stored separately and sanitized.

### Mini governance benchmark

- [ ] At least 30 policy scenarios.
- [ ] Forbidden provider invocations = `0`.
- [ ] Forbidden tool executions = `0`.
- [ ] Duplicate payment executions = `0`.
- [ ] Audit-chain failures = `0`.

## H. Reproducibility

- [ ] Existing `./scripts/preflight` green.
- [ ] Existing `./scripts/stress-rehearse` green.
- [ ] Full Gradle tests green.
- [ ] `scripts/gtc-preflight` exists and passes.
- [ ] `scripts/gtc-demo` exists and is documented.
- [ ] Real provider mode never silently falls back to deterministic mode.
- [ ] Final NIM image is pinned.
- [ ] Final local model/quantization is pinned.
- [ ] Final llama.cpp version/commit is pinned.
- [ ] TramAI submodule commit is pinned.
- [ ] Fresh clone rehearsal succeeds.

## I. KTConf isolation

- [ ] `main` remains untouched by contest experiments unless explicitly chosen later.
- [ ] Deterministic KTConf stage path remains usable.
- [ ] Contest provider configuration is opt-in/separate.
- [ ] No KTConf safety claim is silently broadened for contest marketing.

## J. 60-second video storyboard

Target:

```text
00-08  Real PDF arrives; privacy label shown.
08-18  Wrong GLOBAL route proposed for EU-only document -> DENIED -> delta 0.
18-30  Same document -> Nebius France -> NVIDIA H200 -> NIM/Nemotron -> success.
30-39  Restricted document -> LOCAL NVIDIA only -> success.
39-51  Nemotron proposes EUR 18,400 payment -> HUMAN APPROVAL REQUIRED.
51-56  Approve -> payment 0 -> 1; duplicate remains 1.
56-60  Audit chain VALID + "The model reasons. TramAI governs."
```

Video gates:

- [ ] Real application running; no slide deck as primary proof.
- [ ] NVIDIA appears naturally through model/provider/runtime evidence.
- [ ] Text is readable on mobile/social video.
- [ ] No API keys/tokens/endpoint credentials visible.
- [ ] No long terminal waits.
- [ ] No dependency on an untested live command.
- [ ] Final cut <= target length or current contest expectations.

## K. README/judge experience

The judge should reach this sequence:

```text
README hero/problem
 -> 60-second video
 -> architecture diagram
 -> three proof scenarios
 -> quickstart / deterministic reproduction
 -> optional real-provider reproduction
 -> evidence
 -> claims boundary
```

- [ ] No need to read the full TramAI framework docs to understand the entry.
- [ ] "What is real vs simulated" is explicit.
- [ ] "What we do not claim" is explicit.
- [ ] NVIDIA technology list is concise and tied to actual architecture.

## L. Claims review

Allowed only with corresponding evidence:

- [ ] "Denied before provider invocation."
- [ ] "EU route ran in Nebius `eu-west1` France."
- [ ] "EU route ran on NVIDIA H200."
- [ ] "EU route used NVIDIA NIM."
- [ ] "Global route used NVIDIA-hosted Nemotron."
- [ ] "Restricted route stayed on local NVIDIA inference for the demonstrated run."
- [ ] "High-risk payment required human approval."
- [ ] "Duplicate resume did not duplicate the demo side effect."

Do not claim without additional proof:

- [ ] no "EU AI Act compliant" claim;
- [ ] no blanket "sovereign cloud" legal claim;
- [ ] no "zero egress" claim for EU cloud merely from application policy;
- [ ] no automatic DLP/classification claim;
- [ ] no global production exactly-once claim.

## M. Final freeze

Engineering target: **2026-09-08**.

- [ ] Stop adding P2 features.
- [ ] Create final tag/commit identity.
- [ ] Run deterministic gates from clean clone.
- [ ] Run GLOBAL real proof.
- [ ] Run EU real proof.
- [ ] Run LOCAL real proof.
- [ ] Run 10 consecutive scripted demo rehearsals.
- [ ] Record final video from frozen commit.
- [ ] Review video frame-by-frame for secrets/private data.
- [ ] Publish repository link/video/post.
- [ ] Submit through required contest mechanism.
- [ ] Capture proof of submission.
