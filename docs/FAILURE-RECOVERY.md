# Failure recovery

Stage-safe recovery steps, shortest first.

## Symptom → fix

| Symptom | Fix |
|---|---|
| `demo: command not found` or wrong output | Rebuild: `./gradlew :app:installDist` |
| Scenario fails after code changes | `./scripts/preflight` (runs full test suite) |
| `.build/demo/` has stale artifacts | `./scripts/demo reset` |
| `submodule HEAD` mismatch in preflight | Restore the committed Gitlink — never guess a revision: `git submodule update --init --recursive --checkout`, then `./scripts/preflight` |
| `typed --real` times out / rejects / endpoint disappears | Do NOT troubleshoot the model on stage. State that the live-model path is optional. Continue with `invalid`, `restricted`, `approval`, `evidence` — the deterministic guarantees are unaffected |
| JDK errors / wrong Java version | Export `JAVA_HOME` pointing at a JDK 21 install |
| Interactive approval stuck | `[q]` aborts; `[a]`/`[d]` decide. In scripts use `./scripts/demo all` |

## If TramAI itself misbehaves on stage

The demo must **never** be patched around a TramAI defect from this
repository. The core rule:

> Stop the affected scenario, state the observed vs expected behavior, and
> file the defect as an upstream TramAI task. Do not work around it with
> conference-side logic.

## Reset

```bash
./scripts/demo reset   # clears .build/demo
./scripts/rehearse     # clean full pass
```
