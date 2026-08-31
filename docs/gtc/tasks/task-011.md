# task-011 — GTC Governance Console (Vue/Vite)

Status: **IN_PROGRESS**  
Track: UI / submission presentation  
Owner branch: `task/011-gtc-governance-console`  
Primary dependency: task-005 PDF contract is implemented and in review.  
Final acceptance depends on tasks 006–008 exposing the completed three-boundary, payment, and evidence flows.

## Objective

Build a single-screen Vue 3 + Vite governance console that makes the contest proof understandable without reading terminal output or JSON.

The console is a visualization of backend evidence, not a second policy engine.

North-star screen:

```text
real PDF
  -> trusted classification/residency
  -> LOCAL / EU_CLOUD / GLOBAL_CLOUD decision
  -> real NVIDIA route and provider call counters
  -> Nemotron typed assessment
  -> HIGH-risk tool suspension
  -> human approve/deny
  -> payment execution count
  -> audit-chain evidence
```

## Scope

1. Add a dedicated `frontend/` Vue/Vite application.
2. Use Vue 3 SFCs; keep dependencies intentionally small.
3. Provide drag/drop PDF upload backed by `POST /invoices/analyze-pdf`.
4. Read governance counters from `GET /governance/stats` before/after execution.
5. Visualize all three target NVIDIA boundaries:
   - LOCAL — NVIDIA RTX / local Nemotron;
   - EU_CLOUD — Nebius France / NVIDIA H200 / NVIDIA NIM / Nemotron;
   - GLOBAL_CLOUD — Build.NVIDIA.com / hosted Nemotron.
6. Clearly distinguish `ALLOWED`, `DENIED`, and `SELECTED` states.
7. Never relabel legacy `LOCAL` / `CLOUD` responses as NVIDIA routes while task-006 is incomplete.
8. Display the typed invoice assessment from the backend.
9. Support approval/deny through the existing `/approvals/{id}` endpoints when a pending approval is returned.
10. Refresh real payment/provider counters after actions.
11. Fetch and display audit-chain validity when evidence is available.
12. Add Vite development proxy to the Spring Boot app on port 8080.
13. Configure production build output for Spring static resources under `/gtc/` without committing generated assets.
14. Add small helper scripts for development/build.

## Non-goals

- No authentication/user management.
- No general SaaS dashboard/navigation system.
- No frontend-owned classification, residency, routing, approval, or audit decisions.
- No fake success counters or fabricated audit events.
- No rewrite of tasks 006–008 from the UI branch.
- No extra frontend framework, state library, component library, or icon package unless justified by a concrete demo need.

## Data authority rule

The UI may derive presentation-only state such as which cards should visually be allowed from trusted metadata, but it MUST label the backend-selected route separately and MUST NOT present a derived visual as evidence that TramAI authorized execution.

All proof claims must come from backend responses/counters/evidence.

## Initial backend contracts

Current endpoints available to bind now:

- `GET /governance/healthz`
- `GET /governance/stats`
- `POST /invoices/analyze-pdf`
- `POST /approvals/{approvalId}/approve`
- `POST /approvals/{approvalId}/deny`
- `GET /approvals/{approvalId}/evidence`

Tasks 006–008 may extend/normalize these contracts. The UI should adapt without duplicating business logic.

## Acceptance criteria

### First slice

- [x] branch created from `contest/gtc-nvidia-submission`;
- [x] task and board entry created;
- [x] Vue/Vite scaffold authored;
- [x] single-screen Governance Console authored;
- [x] PDF upload wired to the real backend endpoint;
- [x] stats/counter calls wired;
- [x] approval/deny/evidence calls wired;
- [x] legacy route responses are shown honestly rather than mapped to NVIDIA;
- [ ] dependency install succeeds in a networked environment;
- [ ] `npm run build` passes;
- [ ] visual smoke completed against a running Spring app.

### Final task acceptance

- [ ] actual PDF metadata is visible before/with model result;
- [ ] LOCAL / EU_CLOUD / GLOBAL_CLOUD are visibly distinct;
- [ ] real selected NVIDIA route is visible;
- [ ] denied route proof shows provider delta `0` from backend evidence;
- [ ] assessment and requested high-risk action are visible;
- [ ] approval and denial operate against the real backend;
- [ ] payment count proves no execution before approval and exactly one afterward;
- [ ] duplicate approval rejection is surfaced;
- [ ] audit chain status comes from real evidence;
- [ ] UI works at the recording resolution without scrolling through critical proof states;
- [ ] no secrets/private endpoints are rendered;
- [ ] Vite production build is reproducible and can be packaged with Spring Boot;
- [ ] deterministic KTConf backend path remains unchanged/green.

## Verification

Required before moving to REVIEW:

```bash
./scripts/gtc-ui-build
./scripts/stage-up
./scripts/gtc-ui-dev
```

Then exercise at least:

1. valid synthetic PDF;
2. malformed/missing trusted metadata error;
3. real or deterministic typed result;
4. approval/deny path when available;
5. backend-offline state.

Record browser-visible outputs and exact backend counters in the handoff.

## Current checkpoint

The first Vue/Vite slice has been authored on this branch. The execution environment used to create the branch could not resolve `registry.npmjs.org` (`EAI_AGAIN`), so dependency installation and `npm run build` are explicitly **not claimed yet**. The source uses current Vue 3 / Vite 8 versions and must be verified by the next networked agent before REVIEW.

## Handoff requirement

Update `docs/gtc/BOARD.md` and `docs/gtc/CURRENT-STATE.md` when the task reaches REVIEW/DONE. Include:

- frontend commit SHA;
- Node/npm versions;
- exact dependency/build result;
- screenshot/video rehearsal notes;
- API mismatches discovered with tasks 006–008;
- any remaining recording blockers.
