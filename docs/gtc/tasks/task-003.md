# task-003 — LOCAL NVIDIA RTX + Nemotron

Status: `DONE`
Track: C
Milestone: M2
Depends on: none
Blocks: task-006

## Objective

Prove a real LOCAL NVIDIA inference path on an RTX GPU using a compact Nemotron model and expose it through an OpenAI-compatible endpoint consumable by the existing application.

## Preferred target

- Hardware: available NVIDIA RTX host
- Runtime: `llama.cpp` unless a better already-supported local runtime is justified
- Model candidate: NVIDIA Nemotron 3 Nano 4B GGUF, quantized to fit comfortably on the target GPU
- Trust zone: `LOCAL`

Verify the exact current model artifact/repository and license before pinning.

## Scope

- Download/pin a suitable NVIDIA Nemotron local artifact.
- Bring up local inference independently of the application first.
- Verify structured JSON/tool-capable behavior needed by the demo.
- Integrate using the existing OpenAI-compatible provider abstraction and counting seam.
- Add opt-in contest configuration without removing the current Qwen/Z840 KTConf path.
- Record model revision/hash or other reproducibility evidence where practical.

## Non-goals

- Do not optimize tokens/sec beyond what is needed for a smooth demo.
- Do not make the KTConf deterministic path depend on an RTX GPU.
- Do not claim artifact attestation beyond what is actually verified.

## Acceptance criteria

- [x] Local Nemotron starts reliably on NVIDIA RTX hardware.
- [x] OpenAI-compatible health/inference smoke succeeds.
- [x] Application receives a typed TramAI result from the real local model.
- [x] Provider is declared `LOCAL` through governance configuration, not inferred from URL.
- [x] Invocation counting works.
- [x] Startup/rehearsal steps are documented and bounded.
- [x] Existing deterministic and optional Qwen paths remain intact.

## Verification

Capture sanitized evidence for GPU/model/runtime versions, model artifact identity, startup command, direct smoke, application smoke, and provider counter behavior.

## Handoff

State the exact model artifact, quantization, serving command, base URL convention, expected startup time, changed files, tests, and demo risks.

Closure evidence: [`../evidence/local-nvidia-smoke.md`](../evidence/local-nvidia-smoke.md).
