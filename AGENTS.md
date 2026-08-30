# AGENTS.md — NVIDIA GTC contest branch

This file applies to `contest/gtc-nvidia-submission`.

## Read first

Before editing code, read:

1. `GTC-2026-SUBMISSION.md`
2. `docs/gtc/ROADMAP.md`
3. `docs/gtc/ARCHITECTURE.md`
4. `docs/gtc/NVIDIA-NEBIUS.md`
5. `docs/gtc/AGENT-GUIDE.md`
6. `docs/gtc/SUBMISSION-CHECKLIST.md`

Then read the existing KTConf `README.md`, `docs/CLAIMS-BOUNDARY.md`, `docs/ARCHITECTURE.md`, and `docs/DEMO-SCRIPT.md` before modifying established behavior.

## Branch mission

Convert the existing typed AI boundaries demo into a real NVIDIA Golden Ticket submission without weakening or destabilizing the KTConf deterministic path.

North-star statement:

> **The model reasons. TramAI governs where it runs and what it may do.**

Target architecture:

```text
LOCAL        -> NVIDIA RTX -> local Nemotron
EU_CLOUD     -> Nebius France -> NVIDIA H200 -> NVIDIA NIM -> Nemotron
GLOBAL_CLOUD -> Build.NVIDIA.com -> hosted Nemotron
```

A real PDF carries trusted classification/residency metadata. Metadata is parsed locally before inference. TramAI authorizes placement. Nemotron may propose a payment, but the existing HIGH/HUMAN_REQUIRED tool policy controls execution.

## Non-negotiables

- Do not commit secrets.
- Do not weaken existing policy, approval, exactly-once-demo, or audit tests.
- Do not make deterministic CI depend on network/GPU/provider availability.
- Do not fake `EU_CLOUD` semantics in application code if pinned TramAI lacks the concept; implement the governance capability correctly upstream.
- Do not classify a PDF by first sending it to a remote model.
- Do not claim EU/legal sovereignty or regulatory compliance without evidence.
- Do not silently fall back to deterministic providers when a real-provider run was explicitly requested.
- Keep logical model/provider identity separate from actual deployment model IDs.
- Keep provider invocation counters around all routes used for pre-invocation denial proofs.

## Work order

P0 order is defined in `docs/gtc/ROADMAP.md`.

Independent early tracks:

- M0 trust-zone/baseline
- M1 GLOBAL NVIDIA
- M2 LOCAL NVIDIA
- M3 Nebius EU NVIDIA
- M4 PDF + trusted metadata

Integration begins after independent provider proofs exist.

## Handoff

Every completed task must state exact tests/gates and update `docs/gtc/CURRENT-STATE.md` once M0 creates it.

Use the handoff format in `docs/gtc/AGENT-GUIDE.md`.
