# task-013 — Governed document history

Status: **REVIEW**  
Track: UI / workflow evidence  
Owner branch: `contest/gtc-nvidia-submission`  
Depends on: task-006, task-007, task-008, task-011

## Objective

Expose a backend-owned history of uploaded synthetic PDFs and their governed
workflow so the dashboard can show what happened after the upload, including
route, provider result, tool/approval lifecycle, payment, notifications, and
audit-chain evidence.

## Scope

- Record PDF uploads in a demo-scoped in-memory history store.
- Expose list/detail read-only governance endpoints.
- Update history when approval/denial and audit evidence complete.
- Add a dashboard History page and document detail view.
- Keep all decisions and evidence sourced from backend responses.

## Non-goals

- No persistent database or cross-user history.
- No frontend-owned policy, routing, approval, payment, or audit decisions.
- No real document contents or credentials in history output.

## Acceptance

- [x] PDF uploads appear in history.
- [x] Detail view shows metadata, route, provider, assessment, tool, approval,
      payment, notification, and audit information when available.
- [x] Timeline explains upload and automatic approval for workflows without a
      human approval gate.
- [x] Denied forced-route PDF attempts remain visible with their selected route,
      TramAI denial reason, and provider-not-invoked outcome.
- [x] History survives navigation but is documented as reset on backend restart.
- [x] History has explicit dashboard navigation from the top bar and Overview.
- [x] Deterministic backend tests and frontend build remain green.

## Evidence

`DocumentHistoryService` records only PDF uploads, keeps invoice content to the
existing synthetic invoice fields, and updates approval/audit state through the
backend services. `GET /governance/documents` lists records and
`GET /governance/documents/{id}` returns the selected workflow detail. The
frontend build and full application test suite pass. Denied forced-route
uploads are retained with `DENIED`, the policy reason code, and a timeline event
proving the provider was not invoked.
