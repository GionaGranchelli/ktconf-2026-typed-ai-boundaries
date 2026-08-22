# Demo script (stage)

Show code → execute command → inspect result. No live coding on stage, no
Spring restarts on stage.

## Setup on the stage laptop (before the talk)

```bash
git clone --recursive https://github.com/GionaGranchelli/ktconf-2026-typed-ai-boundaries
cd ktconf-2026-typed-ai-boundaries
./scripts/preflight     # ~2–4 min, needs network once: pin check + full suite + bootJar
./scripts/stage-up      # starts demo :8080, broken :8081, cloud-routing :8082
# optional: airplane mode test
./scripts/stress-rehearse
```

## Conference morning (freeze check)

```bash
git checkout ktconf-2026-demo-v3   # the Git checkout establishes identity
git submodule update --init --recursive --checkout
./scripts/preflight          # pinned TramAI revision + full deterministic suite + jar
./scripts/stress-rehearse    # the conference gate: 60/60 (20/20 per profile)
./scripts/preflight-real     # env -> endpoint -> model -> real-profile test MUST succeed
./scripts/demo real          # manually inspect the real-model output once
```

After this: no pulls, no dependency updates, no TramAI repins.

## The talk

1. **Open `app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt`**
   — the typed boundary. One interface, one method, typed in / typed out.
2. **Open `app/src/main/kotlin/dev/giona/ktconf/governance/DemoConfiguration.kt`**
   — real TramAI wiring: providers, trust zones, tools, approvals — as an
   infrastructure bean, behind a normal Spring application.
3. **`./scripts/stage-up` already running** — point at the four ports once,
   then never again.

Then run, in order:

```bash
./scripts/demo typed          # :8080  valid model output → typed InvoiceAssessment (200)
./scripts/demo real           # :8083  same typed input/output contract, real LLM — prove it isn't fake
./scripts/demo invalid        # :8081  model goes off-script → engine rejects (422), 0 side effects
./scripts/demo restricted     # :8082  RESTRICTED → cloud denied BEFORE invocation (count=0)
./scripts/demo payment        # :8080  202 AWAITING_APPROVAL — request is finished
./scripts/demo approve <id>   #        workflow resumes, payment 0 → 1
./scripts/demo approve <id>   #        again → 409, payment stays 1
./scripts/demo evidence <id>  #        real audit chain, 4 ordered events, verified
./scripts/demo stats          #        cloudInvocationCount=0, paymentExecutionCount=1
```

Key line for the room: *“The HTTP request is finished. The workflow
isn't.”* Then: *“And suddenly this doesn't look like an AI problem anymore.
It looks like distributed systems.”*

The `real` command needs `KTCONF_DEMO_LOCAL_BASE_URL` and
`KTCONF_DEMO_LOCAL_MODEL` (OpenAI-compatible endpoint the operator
intentionally treats as LOCAL — e.g. Ollama on the laptop, a private LAN
host; add `KTCONF_DEMO_LOCAL_API_KEY` for authenticated endpoints). Since
the upstream enum-schema fix (tramAI #261/#262, pinned here), the real path
is expected to SUCCEED. A rejection on stage is a safe outcome but means the
live-model demo failed: state that the live-model path is optional and
continue with the deterministic instances (the `invalid` instance
demonstrates rejection on purpose). `real` is never part of the
deterministic gate.

## Expected outputs (abridged)

- `typed`: HTTP 200, risk=LOW, recommendedAction=REVIEW_ONLY, invoiceId KTCONF-001
- `real`: HTTP 200, same typed shape — a real LLM produced the InvoiceAssessment
- `invalid`: HTTP 422 `{"code":"structured-output-rejected",...}`, stats payment 0
- `restricted`: HTTP 403 `{"code":"classification-routing-blocked",...}`, cloud invocation count 0
- `payment`: HTTP 202 `{"status":"AWAITING_APPROVAL","approvalId":...,"workflowRunId":...}` — no token
- `approve <id>`: HTTP 200 typed assessment, stats payment 0 → 1
- `approve <id>` again: HTTP 409, stats payment stays 1
- `deny <id>`: HTTP 200 `{"status":"DENIED",...}`, resume afterwards → 409, payment stays 0
- `evidence <id>`: 4 ordered audit events (SUSPENDED → BEFORE_RESUME → RESUMED → COMPLETED), `chainValid:true`, pack under `.build/demo/evidence/`

## Failure recovery

See [FAILURE-RECOVERY.md](FAILURE-RECOVERY.md). In short:
`./scripts/stage-down && ./scripts/stage-up`, re-run `./scripts/preflight`,
or rebuild with `./gradlew :app:bootJar`.
