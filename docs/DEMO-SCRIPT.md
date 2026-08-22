# Demo script (stage)

Show code → execute command → inspect result. No live coding on stage.

## Setup on the stage laptop (before the talk)

```bash
git clone --recursive https://github.com/GionaGranchelli/ktconf-2026-typed-ai-boundaries
cd ktconf-2026-typed-ai-boundaries
./scripts/preflight     # ~1–2 min, needs network once
# optional: airplane mode test
./scripts/rehearse
```

## The talk

1. **Open `app/src/main/kotlin/dev/giona/ktconf/ai/InvoiceAnalysisService.kt`**
   — the typed boundary. One interface, one method, typed in / typed out.
2. **Open `app/src/main/kotlin/dev/giona/ktconf/runtime/DemoRuntimeFactory.kt`**
   — real TramAI wiring: profile, providers, trust zones, tools, approvals.

Then run, in order:

```bash
./scripts/demo typed          # provider response → typed boundary
./scripts/demo invalid        # model goes off-script → engine rejects, 0 side effects
./scripts/demo restricted     # RESTRICTED → cloud denied BEFORE invocation (count=0)
./scripts/demo approval       # [a]pprove / [d]eny / [q]uit — interactive
./scripts/demo evidence       # real audit chain, verified, written to .build/demo/
./scripts/demo all            # (if time) full pass, auto-approve
./scripts/demo typed --real   # optional: real LLM behind the same boundary
```

The `--real` command needs `KTCONF_DEMO_LOCAL_BASE_URL` and
`KTCONF_DEMO_LOCAL_MODEL` (OpenAI-compatible endpoint, e.g. Ollama).
The real model either produces a typed result or output the engine rejects —
both are honest; the boundary holds both ways. It is NOT part of `demo all`.

## Expected outputs (abridged)

- `typed`: `typed result escaped = YES`, risk=LOW, recommendedAction=REVIEW_ONLY
- `invalid`: `typed result escaped = NO`, `StructuredOutputException`, `payment execution = 0`
- `restricted`: `denied reason = ... classification-routing-blocked`, `cloud invocations = 0`, LOCAL → typed result
- `approval`: `payment at suspend = 0` → decision → `payment after resume = 1` → duplicate resume rejected, payment stays 1
- `evidence`: audit events table + `hash chain valid = true`, raw JSON under `.build/demo/evidence/`

## Failure recovery

See [FAILURE-RECOVERY.md](FAILURE-RECOVERY.md). In short: `./scripts/demo reset`,
re-run `./scripts/preflight`, or rebuild the binary with `./gradlew :app:installDist`.
