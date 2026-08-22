# Failure recovery

Stage-safe recovery steps, shortest first.

## Symptom → fix

| Symptom | Fix |
|---|---|
| Instance down / port answers nothing | `./scripts/stage-down && ./scripts/stage-up` (rebuilds the current jar, waits for each `/healthz`, verifies each profile) |
| Wrong instance on a port / false health | `./scripts/stage-down && ./scripts/stage-up` — `/healthz` reports the profile, so a stale process cannot pass |
| Scenario fails after code changes | `./scripts/preflight` (runs full test suite) |
| `.build/demo/` has stale evidence | Remove `.build/demo/evidence/*` or re-run the scenario |
| `submodule HEAD` mismatch in preflight | Restore the committed Gitlink — never guess a revision: `git submodule update --init --recursive --checkout`, then `./scripts/preflight` |
| `real` times out / rejects / endpoint disappears | Do NOT troubleshoot the model on stage. State that the live-model path is optional. Continue with `typed`, `invalid`, `restricted`, `payment` — the deterministic guarantees are unaffected |
| JDK errors / wrong Java version | Export `JAVA_HOME` pointing at a JDK 21 install |
| Approval flow stuck | A fresh suspension yields a fresh `approvalId` — just re-run `./scripts/demo payment` and act on the new id |

## If TramAI itself misbehaves on stage

The demo must **never** be patched around a TramAI defect from this
repository. The core rule:

> Stop the affected scenario, state the observed vs expected behavior, and
> file the defect as an upstream TramAI task. Do not work around it with
> conference-side logic.

## Reset

```bash
./scripts/stage-down        # stop all instances
./scripts/stage-up          # start them again, profiles verified
./scripts/stress-rehearse   # conference gate: 60/60
```
