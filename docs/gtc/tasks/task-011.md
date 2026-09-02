# task-011 — GTC Governance Console (Vue/Vite)

Status: **REVIEW**  
Track: UI / submission presentation  
Owner branch: `contest/gtc-nvidia-submission`  
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
   - LOCAL — NVIDIA RTX / local Qwen action model;
   - EU_CLOUD — Scaleway Generative APIs serverless / Mistral Medium 3.5 128B;
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
- [x] dependency install succeeds in a networked environment;
- [x] `npm run build` passes;
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

The Vue/Vite console is now bootable from this contest branch. Its entrypoint is
`frontend/index.html` → `frontend/src/main.js` → `frontend/src/App.vue`; development runs on port 3001 and
proxies the Spring API on port 8080. `npm install` and `npm run build` pass on
Node 22.23.1 / npm 10.9.8. The remaining gate is a browser visual smoke against
the running Spring application; no live provider or frontend-generated claim is
being added by this UI task.

The workflow trace now has an explicit terminal `Flow complete` state. A typed
result with no pending tool call completes there and labels tool interception
and human decision `NOT REQUIRED`; a high-risk result still proceeds through
TramAI suspension, human decision, evidence, and then completion. This is a
presentation state derived from backend responses, not frontend authorization.
The consequential-action panel also exposes a prominent backend-backed
Payment status: `NOT REQUESTED`, `AWAITING APPROVAL`, `SCHEDULED` with the
exactly-once ledger count, or `DENIED` with no side effect.
For a suspended PDF payment request, section 03 now displays the backend's
locally parsed invoice context (amount, supplier, invoice ID, and description)
while clearly labelling it as pre-approval context rather than a typed model
assessment.
Slow provider and approval operations now show a full-screen progress overlay
with the current governed phase and an explicit no-side-effect-until-authorized
message. Audit evidence is rendered with human-readable lifecycle labels,
decision/actor/tool details, timestamps, and a shortened event-hash fingerprint.

The Overview page is now the default landing screen for recording: it explains
the problem, three guarantees, governed architecture, NVIDIA relationship and
the typical-application comparison before linking into the real live proof.
Presentation mode hides the sidebar and nonessential telemetry. The placement
and action CTAs open the existing live workflow with a scenario hint; they do
not execute or decide anything in the browser. `npm run build` passes on Node
22.23.1 / npm 10.9.8, and a Vite-only visual smoke passed at 1440x1000.

## Handoff requirement

Update `docs/gtc/BOARD.md` and `docs/gtc/CURRENT-STATE.md` when the task reaches REVIEW/DONE. Include:

- frontend commit SHA;
- Node/npm versions;
- exact dependency/build result;
- screenshot/video rehearsal notes;
- API mismatches discovered with tasks 006–008;
- any remaining recording blockers.
