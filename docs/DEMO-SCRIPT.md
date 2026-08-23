# Demo script (stage)

Show code → execute command → inspect result. No live coding on stage, no
restarts on stage.

## Setup on the stage laptop (before the talk)

```bash
git clone --recursive https://github.com/GionaGranchelli/ktconf-2026-typed-ai-boundaries
cd ktconf-2026-typed-ai-boundaries
./scripts/preflight     # ~2–4 min, needs network once: pin check + full suite + bootJar
./scripts/stage-up      # ONE app on :8080 (deterministic providers)
./scripts/stress-rehearse
```

## Conference morning (freeze check)

```bash
git checkout ktconf-2026-demo-v3   # the freeze tag establishes identity
git submodule update --init --recursive --checkout
./scripts/preflight          # pinned TramAI revision + full deterministic suite + jar
./scripts/stress-rehearse    # the conference gate: the full oracle 20/20
./scripts/stage-up
./scripts/demo typed
./scripts/demo restricted
./scripts/demo restricted-cloud
./scripts/demo invalid
```

After this: no pulls, no dependency updates, no TramAI repins.

## The talk

1. **Open `app/src/main/resources/application.yml`** — all policy in one
   file: allowed models, providers, trust zones, tools, permissions.
2. **Open `app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt`**
   — the typed boundary: two operations, two governed model routes.
3. **Open `app/src/main/kotlin/dev/giona/ktconf/application/InvoiceService.kt`**
   — the routing `when`. Say: *"The application chooses a route."*
4. **Open `app/src/main/kotlin/dev/giona/ktconf/payments/SchedulePaymentTool.kt`**
   — tool = authority: HIGH risk, HUMAN_REQUIRED.

Then run, in order:

```bash
./scripts/demo typed            # PUBLIC KTCONF-001 → cloud route (200)
./scripts/demo restricted       # RESTRICTED KTCONF-001 → local route (200)
./scripts/demo restricted-cloud # RESTRICTED forced cloud → 403, cloud delta 0
./scripts/demo invalid          # PUBLIC KTCONF-INVALID → 422, 0 side effects
./scripts/demo payment          # 202 AWAITING_APPROVAL — request is finished
./scripts/demo approve <id>     # workflow resumes, payment 0 → 1
./scripts/demo approve <id>     # again → 409, payment stays 1
./scripts/demo evidence <id>    # real audit chain, 4 ordered events, verified
./scripts/demo stats            # cloudInvocationCount / paymentExecutionCount
```

Key line for the room: *"The HTTP request is finished. The workflow isn't."*
Then: *"And suddenly this doesn't look like an AI problem anymore. It looks
like distributed systems."*

Optional real-model proof — explicitly off-stage, never part of the talk
and never part of the deterministic gate:

```bash
KTCONF_DEMO_LOCAL_BASE_URL=http://localhost:11434/v1 \
KTCONF_DEMO_LOCAL_MODEL=gemma-4-12b-it:q5_k_m \
./scripts/preflight-real      # env → endpoint → model → typed result MUST succeed
```

The real path is opt-in and independent. `stage-up` always starts the
deterministic app (it unsets the real-model env). The endpoint is declared
LOCAL by operator assertion — never by URL, hostname or provider type.

## Expected outputs (abridged)

- `typed`: HTTP 200, `selectedRoute=CLOUD`, risk LOW, invoiceId KTCONF-001
- `restricted`: HTTP 200, `selectedRoute=LOCAL`, same typed shape
- `restricted-cloud`: HTTP 403 `{"code":"classification-routing-blocked",...}` plus a printed cloud invocation delta of 0 (before/after counts)
- `invalid`: HTTP 422 `{"code":"structured-output-rejected",...}`, payment 0
- `payment`: HTTP 202 `{"status":"AWAITING_APPROVAL","approvalId":...,"workflowRunId":...,"toolName":"schedule-payment"}` — no token
- `approve <id>`: HTTP 200 typed assessment, payment 0 → 1
- `approve <id>` again: HTTP 409, payment stays 1
- `deny <id>`: HTTP 200 `{"status":"DENIED",...}`, resume afterwards → 409, payment stays 0
- `evidence <id>`: 4 ordered audit events (SUSPENDED → BEFORE_RESUME → RESUMED → COMPLETED), `chainValid:true`, pack under `.build/demo/evidence/`
- `stats`: `cloudInvocationCount` / `paymentExecutionCount` — the proofs

## Failure recovery

See [FAILURE-RECOVERY.md](FAILURE-RECOVERY.md). In short:
`./scripts/stage-down && ./scripts/stage-up`, re-run `./scripts/preflight`,
or rebuild with `./gradlew :app:bootJar`.
