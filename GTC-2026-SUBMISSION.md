# NVIDIA GTC Berlin 2026 — Golden Ticket submission

> Working title: **The Model Is Not the Authority**
>
> Technical tagline: **One Spring application. One TramAI policy plane. Three NVIDIA execution boundaries.**

This branch is an isolated contest workstream. It MUST NOT destabilize the KTConf conference path on `main`.

## North-star objective

Build a short, real, reproducible open-source demo proving that an NVIDIA-powered AI application can use different execution boundaries while **deterministic runtime policy — not the model — remains the final authority over data placement and consequential actions**.

The contest demo should make this understandable in under 60 seconds:

1. A real PDF arrives with trusted classification/residency metadata.
2. Metadata is read locally before document content is sent to any model.
3. TramAI evaluates which execution boundary is allowed.
4. NVIDIA inference runs only in an allowed boundary:
   - `LOCAL`: NVIDIA RTX + local Nemotron.
   - `EU_CLOUD`: Nebius AI Cloud in an EU region on NVIDIA GPU + self-hosted NVIDIA NIM/Nemotron.
   - `GLOBAL_CLOUD`: NVIDIA-hosted NIM API from Build.NVIDIA.com.
5. A deliberately wrong route is denied **before provider invocation** and the provider counter proves delta `0`.
6. Nemotron proposes a consequential payment action.
7. TramAI suspends execution because the tool is `HIGH` risk and requires human approval.
8. Approval resumes the workflow exactly once; duplicate approval remains rejected.
9. Hash-chained audit evidence verifies the execution.

The final message is:

> **The model reasons. TramAI governs where it runs and what it may do.**

## Contest optimization target

The implementation and presentation are designed explicitly around the four equally weighted contest criteria:

| Criterion | What this branch must prove | Target |
|---|---|---:|
| Technical innovation | Runtime authority is independent of model output and application routing | 9+/10 |
| NVIDIA / partner technology | NVIDIA model + NVIDIA hosted API + NVIDIA local GPU + NVIDIA NIM on Nebius NVIDIA GPU | 9+/10 |
| Impact / usefulness | Real enterprise document/data-residency + high-risk action governance | 9+/10 |
| Documentation / presentation | Reproducible repo, visible evidence, 60-second working demo | 9+/10 |

Contest deadline: **2026-09-10**. Treat September 8 as the engineering freeze target so there is time to record, edit, publish and verify the final submission.

## Locked product decisions

These are deliberate decisions. Agents should not silently replace them.

### 1. Use the existing KTConf application as the foundation

Do not build a second toy application. The current repository already contains the strongest proof primitives:

- classification-aware provider enforcement;
- provider invocation counters;
- deliberate wrong-route test;
- structured output validation;
- governed `schedule-payment` tool;
- human approval suspension/resume;
- duplicate-resume rejection;
- exactly-once demo ledger semantics;
- hash-chained audit evidence.

The contest work adapts the inference topology and input surface, not the fundamental governance story.

### 2. Preserve the KTConf deterministic oracle

The deterministic stage path remains valuable for CI and reproducibility. Real NVIDIA providers are an additional contest proof path.

Never make normal tests, `preflight`, or the KTConf stage dependent on:

- internet access;
- NVIDIA API availability;
- Nebius availability;
- a local GPU;
- a secret being present.

### 3. The demo uses a real document

The contest input should be an actual PDF rather than only JSON sent through curl.

The trusted metadata/classification MUST be available and parsed **before document content can egress**. Do not use a remote LLM to decide whether it was permissible to send the document remotely.

Preferred demo metadata contract:

```text
classification = PUBLIC | CONFIDENTIAL | RESTRICTED
residency      = ANY | EU_ONLY | LOCAL_ONLY
```

A custom PDF/XMP field or a trusted sidecar/envelope is acceptable for the first contest version. Do not claim compatibility with Microsoft Purview or another commercial labeling system unless that exact integration is implemented and tested.

### 4. Three execution boundaries

Target policy:

| Document policy | Allowed boundary | Primary contest route |
|---|---|---|
| `PUBLIC / ANY` | global, EU, local | `GLOBAL_CLOUD` |
| `CONFIDENTIAL / EU_ONLY` | EU, local | `EU_CLOUD` |
| `RESTRICTED / LOCAL_ONLY` | local only | `LOCAL` |

The central proof is not automatic model selection. It is that a selected route is independently authorized or denied before provider invocation.

### 5. NVIDIA everywhere that matters

Preferred topology:

- **GLOBAL**: Build.NVIDIA.com hosted API (`https://integrate.api.nvidia.com/v1`) using `nvidia/nemotron-3.5-lightning-30b-a3b` as the first candidate.
- **EU**: Nebius AI Cloud `eu-west1` (France) on one NVIDIA H200 using the official NVIDIA NIM image for the chosen Nemotron model.
- **LOCAL**: local NVIDIA RTX using `nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF` via llama.cpp as the first candidate.

Model choice can change only when a milestone proves incompatibility. Prefer the Nemotron family across all boundaries so the story is about **placement policy**, not model-vendor switching.

### 6. Nebius is the EU NVIDIA partner story

Preferred EU deployment is **Nebius `eu-west1` (France), NVIDIA H200, self-hosted NVIDIA NIM**.

Do not use the discontinued Nebius one-click NVIDIA NIM standalone application.

Preferred implementation path is Nebius Serverless AI endpoint because it can run a container image on an NVIDIA GPU and can authenticate to a private registry such as NVIDIA NGC (`nvcr.io`). A raw VM is fallback if NIM startup or endpoint constraints require more control.

### 7. No secrets in git

Never commit:

- `NVIDIA_API_KEY`;
- `NGC_API_KEY`;
- Nebius access tokens;
- Nebius endpoint auth tokens;
- registry passwords;
- real customer documents.

Use environment variables locally and Nebius MysteryBox/secret references for cloud deployment.

## Desired final architecture

```text
                         REAL PDF
                            |
                   local metadata read
                     (zero AI egress)
                            |
                            v
                    application routing
                            |
                    +-------+-------+
                    |   TramAI      |
                    | policy plane  |
                    +-------+-------+
                            |
             +--------------+---------------+
             |              |               |
             v              v               v
           LOCAL         EU_CLOUD       GLOBAL_CLOUD
             |              |               |
        NVIDIA RTX      Nebius France     NVIDIA API
             |          NVIDIA H200         Catalog
        llama.cpp            |               |
             |          NVIDIA NIM           |
      Nemotron 3 Nano        |        Nemotron 3.5 Lightning
            4B          Nemotron family      |
             \              |               /
              +-------------+--------------+
                            |
                    typed assessment
                            |
                  schedule-payment(...)
                            |
                   TramAI tool policy
                            |
                HIGH / HUMAN_REQUIRED
                            |
                     approve / deny
                            |
                   exactly-once demo
                            |
                  hash-chain evidence
```

## Required contest proofs

The branch is not submission-ready until all P0 proofs below are real and repeatable:

1. `PUBLIC -> GLOBAL_CLOUD -> NVIDIA hosted Nemotron -> HTTP 200 typed result`.
2. `CONFIDENTIAL/EU_ONLY -> EU_CLOUD -> Nebius NVIDIA H200/NIM -> HTTP 200 typed result`.
3. `RESTRICTED/LOCAL_ONLY -> LOCAL -> NVIDIA RTX/local Nemotron -> HTTP 200 typed result`.
4. Forced `EU_ONLY -> GLOBAL_CLOUD` is denied before provider invocation; global NVIDIA invocation delta is `0`.
5. Forced `LOCAL_ONLY -> EU_CLOUD` is denied before provider invocation; Nebius/NIM invocation delta is `0`.
6. A real PDF enters through the same application and metadata is parsed locally before inference.
7. Nemotron proposes a payment action; TramAI returns `AWAITING_APPROVAL` and payment count remains `0`.
8. Approve once -> payment count `1`.
9. Approve the same continuation again -> rejected, payment count stays `1`.
10. Audit/evidence endpoint reports an ordered valid chain for the execution.

## What we deliberately do not claim

- EU region hosting by itself is not called legal or regulatory "sovereignty".
- TramAI does not infer confidentiality from arbitrary document content unless a separate classifier is explicitly implemented.
- The model is not a security boundary.
- Demo exactly-once semantics are scoped to the existing demo ledger/runtime unless stronger persistence is added and proven.
- The deterministic provider path does not measure real-model quality.
- A provider trust-zone declaration is an operator assertion; it is not inferred from a URL.

## Workstream documents

Agents should read these before implementation:

1. [`docs/gtc/ROADMAP.md`](docs/gtc/ROADMAP.md) — milestones, dependencies and acceptance criteria.
2. [`docs/gtc/ARCHITECTURE.md`](docs/gtc/ARCHITECTURE.md) — target runtime and security architecture.
3. [`docs/gtc/NVIDIA-NEBIUS.md`](docs/gtc/NVIDIA-NEBIUS.md) — current provider/deployment research and commands.
4. [`docs/gtc/AGENT-GUIDE.md`](docs/gtc/AGENT-GUIDE.md) — working rules, task ownership and evidence expectations.
5. [`docs/gtc/SUBMISSION-CHECKLIST.md`](docs/gtc/SUBMISSION-CHECKLIST.md) — freeze, recording and publication gates.

## Definition of done

This branch succeeds when a reviewer with no TramAI context can watch a ~60 second screen recording and understand:

> A document carries a trusted handling requirement. The application chooses an inference route. TramAI independently prevents an illegal placement before an NVIDIA provider sees the content. The document is processed by an allowed NVIDIA deployment, the model proposes an action, and TramAI still prevents that action from becoming authority without policy and human approval.

The repository must then provide enough deterministic and real-provider evidence for a technical judge to verify that the video is not a mocked UI story.
