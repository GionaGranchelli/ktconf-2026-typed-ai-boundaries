# task-004 — Nebius EU NVIDIA NIM

Status: `READY`
Track: D
Milestone: M3
Depends on: none
Blocks: task-006

## Objective

Prove the EU execution boundary on Nebius in `eu-west1` (France) using NVIDIA GPU infrastructure and an official NVIDIA NIM/Nemotron deployment.

## Preferred path

`nvcr.io` NIM image -> Nebius secret-backed registry auth -> Nebius Serverless AI / supported GPU endpoint -> NVIDIA H200 -> Nemotron.

Do not assume the old one-click Nebius NIM application still exists.

## Scope

- Verify Nebius `eu-west1` availability, quota, supported NVIDIA GPU preset, and current Serverless AI/custom-container flow.
- Verify the selected official NIM image/model and its GPU memory requirements.
- Determine whether NGC registry credentials are required; if so, use a proper NGC Personal API key and Nebius secret storage.
- Deploy and smoke-test the endpoint before application integration.
- Record region and infrastructure evidence sufficient to substantiate an `EU_CLOUD` deployment claim.
- Preserve an OpenAI-compatible or otherwise clean adapter surface for task-006.

## Fallback

If Serverless AI cannot pull/start the NIM reliably, use a Nebius NVIDIA GPU VM and run the official NIM container directly. Mirroring into Nebius Container Registry is allowed only if necessary for reliability/reproducibility.

## Non-goals

- Do not claim legal sovereignty or regulatory compliance merely because the region is in France.
- Do not commit Nebius/NGC credentials.
- Do not build vendor-specific authorization logic in application code.

## Acceptance criteria

- [ ] Real Nemotron/NIM endpoint runs on NVIDIA GPU infrastructure in Nebius `eu-west1` or another explicitly documented EU Nebius region.
- [ ] Region/GPU/runtime are evidenced and documented.
- [ ] Direct inference succeeds.
- [ ] Auth secrets are stored outside git.
- [ ] Endpoint can be consumed by the application with a stable provider abstraction.
- [ ] Known cold-start/quota/cost risks are documented.
- [ ] Fallback route is documented if managed deployment is not viable.

## Handoff

Provide sanitized deployment steps, exact region/GPU/NIM/model, endpoint protocol, auth mechanism, observed startup/latency, limitations, and changed infrastructure/docs files.
