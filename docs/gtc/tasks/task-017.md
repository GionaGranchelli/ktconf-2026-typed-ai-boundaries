# task-017 — retire redundant invoice endpoints and AI operations

Status: `BLOCKED`
Track: Post-freeze cleanup
Owner branch: `contest/gtc-nvidia-submission`
Depends on: task-010

> Planning task only. Do not start before the submission freeze. The current
> application behavior, governance semantics, provider composition, and
> deterministic rehearsal must remain unchanged until this task is claimed.

## Objective

Reduce the invoice API and `InvoiceAnalysisService` to the smallest truthful
surface needed by the GTC dashboard, while preserving the legacy deterministic
KTConf oracle and independently useful provider smoke commands.

The canonical GTC path is:

```text
POST /invoices/analyze-pdf
        + optional forceRoute
        ↓
trusted metadata → governed route → typed inference → action/approval
```

## Evidence-based inventory

### Canonical GTC PDF operations

These are selected by `InvoiceService.analyze(...)` for the three governed
boundaries and must not be removed during this cleanup:

- `analyzeLocalNvidiaPayment` — HIGH-risk LOCAL payment continuation;
- `analyzeLocalNvidiaAutoPayment` — LOW-risk LOCAL automatic payment;
- `analyzeEuScalewayPayment` — HIGH-risk EU payment continuation;
- `analyzeEuScalewayAutoPayment` — LOW-risk EU automatic payment;
- `analyzeGlobalNvidiaPayment` — HIGH-risk GLOBAL payment continuation;
- `analyzeGlobalNvidiaAutoPayment` — LOW-risk GLOBAL automatic payment.

The canonical PDF path selects the payment or auto-payment operation according
to the trusted amount. The direct typed operations are listed separately below
because they are used by provider-specific smoke and workflow-demo paths, not
by the canonical PDF path.

### Legacy, direct-smoke, or workflow-demo operations to reassess

The call-graph audit found production callers for all of these today; they are
cleanup candidates, not currently dead code:

- `analyzeLocal` and `analyzeLocalAutoPayment` — legacy deterministic `LOCAL`
  route used by `InvoiceService` and retained KTConf behavior;
- `preAssessLocal` — two-phase `WorkflowDemoService` assessment;
- `analyzeCloud`, `analyzeCloudPayment`, and `analyzeCloudAutoPayment` — legacy
  deterministic `CLOUD` route, boundary proof, and retained workflow/
  observability coverage;
- `analyzeGlobalNvidia` — `/invoices/global-nvidia` direct provider smoke and
  `WorkflowDemoService`;
- `analyzeEuScaleway` — `/invoices/eu-scaleway` direct provider smoke and
  `WorkflowDemoService`;
- `analyzeLocalNvidia` — `/invoices/local-nvidia` direct provider smoke and
  `WorkflowDemoService`.

Do not classify interface overrides in tests as independent production usage:
the test doubles implement the complete `InvoiceAnalysisService` contract.
Confirm every non-test caller has been migrated before deleting an operation.

## Scope

- Retire `POST /invoices/analyze/local-nvidia` after migrating its remaining
  JSON payment/observability tests to the canonical PDF endpoint.
- Reassess the direct JSON provider endpoints:
  `/invoices/local-nvidia`, `/invoices/eu-scaleway`, and
  `/invoices/global-nvidia`. Keep them only if provider-specific smoke or
  operational verification still requires them; otherwise move those checks
  to scripts/tests that exercise the canonical PDF flow.
- Reassess the legacy `/invoices/analyze` and
  `/invoices/boundary/restricted-cloud` endpoints only after confirming the
  deterministic KTConf rehearsal no longer depends on them.
- Remove only `InvoiceAnalysisService` operations whose production callers,
  smoke scripts, tests, and documentation have all been migrated or retired.
- Update endpoint documentation, smoke scripts, tests, and task evidence in
  the same change.

## Non-goals

- Do not change `ProviderTrustZone`, routing authorization, approval,
  reissue, payment, notification, history, audit, or telemetry semantics.
- Do not remove the deterministic KTConf route merely because it is not used by
  the browser dashboard.
- Do not merge the explicit `@Operation` declarations into opaque dynamic
  dispatch; the governed model/tool boundaries should remain readable.
- Do not start this task before task-010 and the submission freeze.

## Acceptance criteria

- [ ] `/invoices/analyze/local-nvidia` is retired or its retention is justified
      by a documented non-demo caller.
- [ ] Every remaining `InvoiceAnalysisService` operation has a documented
      production purpose and caller category: GTC PDF, legacy deterministic,
      direct smoke, or workflow demo.
- [ ] No endpoint documented as canonical is backed by a legacy JSON-only path.
- [ ] Direct provider smoke coverage remains available or is replaced by
      equivalent canonical-PDF evidence.
- [ ] Legacy deterministic routes, approval/resume, payment exactly-once,
      history, audit, and provider invocation counters remain green.
- [ ] `./scripts/gtc-evidence`, the frontend build, and all retained live smoke
      commands pass.
- [ ] `BOARD.md` and `CURRENT-STATE.md` record the final removals and evidence.

## Verification plan

Before editing, inventory callers with:

```bash
rg -n 'analyzeLocal|analyzeCloud|analyzeGlobalNvidia|analyzeEuScaleway|preAssessLocal' \
  app/src/main app/src/test scripts docs frontend
```

After editing, run the deterministic evidence gate, frontend build, retained
provider smoke scripts when infrastructure is available, and a repository-wide
search for removed endpoint/operation names. Do not claim live evidence unless
the live command actually ran.

## Handoff

Report the removed endpoint/operations, migrated callers, retained legacy
surface, exact tests and smoke commands, and any provider-specific endpoint
that remains solely for operational verification.
