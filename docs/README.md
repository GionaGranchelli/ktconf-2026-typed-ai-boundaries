# Documentation index

One audience each — start where you are.

**Never seen TramAI?** Read these in order:

1. [Root README](../README.md) — ~2 min: what the demo proves, how to run it
2. [TRAMAI-PRIMER.md](TRAMAI-PRIMER.md) — ~8 min: what is actually happening when a request enters the system
3. [ARCHITECTURE.md](ARCHITECTURE.md) — ~5 min: the Spring application around TramAI
4. [CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md) — ~3 min: real vs simulated, exactly
5. Run `./scripts/demo typed`

| Document | Audience | Read when |
|---|---|---|
| [../README.md](../README.md) | Everyone (QR code) | You want the whole demo in 2 minutes: instances, stage API, quick start, pinned revision |
| [TRAMAI-PRIMER.md](TRAMAI-PRIMER.md) | TramAI newcomers | You have never used TramAI and want the mental model: concepts, responsibilities, one request end to end |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Curious engineers | You want to see how the typed boundary, policy routing, approval and audit are wired to real TramAI |
| [DEMO-SCRIPT.md](DEMO-SCRIPT.md) | The speaker | You are on stage, or running the conference-morning freeze check |
| [FAILURE-RECOVERY.md](FAILURE-RECOVERY.md) | The speaker | Something on stage went wrong |
| [CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md) | Reviewers, skeptics | You want to know exactly what is real vs simulated, and how that was verified |
| [TRAMAI-INTEGRATION.md](TRAMAI-INTEGRATION.md) | Contributors | You want to change the pinned TramAI revision or the composite build |
| [UPSTREAM-ENUM-SCHEMA-BUG.md](UPSTREAM-ENUM-SCHEMA-BUG.md) | Contributors | You want the full record of the enum-schema defect (tramAI #261/#262) this demo exposed |

## FAQ

**Is the demo fake?**
No, but it is honest about what is simulated. The deterministic instances
use a scripted model and an in-memory payment ledger. Everything around
them — structured output, policy routing, tool governance,
approval/continuation, audit/evidence — is real TramAI behavior. The
optional `real` instance replaces the scripted model with an actual LLM. See
[CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md).

**Why are there four instances on four ports?**
One application artifact, different runtime configuration. `./scripts/stage-up`
starts `demo :8080`, `broken :8081`, `cloud-routing :8082` (and `real :8083`
when configured) before the talk; `./scripts/demo <command>` only curls them.
Spring profiles describe running applications — the talk never restarts one.

**Does it need internet?**
Only once. After `./scripts/preflight` and `./scripts/stage-up`, the
deterministic instances run fully offline — airplane mode is the on-stage
proof. The `real` instance needs your model endpoint (a LOCAL/private one
you intentionally trust: Ollama on the laptop, a private LAN host, a
self-hosted inference server).

**Why is TramAI pinned as a submodule instead of a Maven dependency?**
Maven Central 0.5.0 predates the typed-governance surface this demo uses.
The submodule + composite build pins an exact revision, so the stage laptop
runs byte-identical TramAI code — and `./scripts/preflight` fails loudly if
the submodule drifts. See [TRAMAI-INTEGRATION.md](TRAMAI-INTEGRATION.md).

**Why did this repo file a bug against its own dependency?**
The demo found a real TramAI defect: generated enum schemas contradicted the
parser, so no model output could succeed. Instead of patching around it, the
repo stopped and reported it upstream (tramAI #261), the fix landed (#262),
and the repo repinned. That is the core rule: never patch TramAI from this
repository. See [UPSTREAM-ENUM-SCHEMA-BUG.md](UPSTREAM-ENUM-SCHEMA-BUG.md).

**Is the payment real?**
No — an in-memory ledger with idempotency keys. The *behavior* it
demonstrates (exactly-once, duplicate-resume rejection) is real; the money
is not. `GET /governance/stats` shows the counter on stage.

**Can I use my own model?**
Any OpenAI-compatible **LOCAL/private** endpoint you intentionally treat as
LOCAL — Ollama on the laptop, a private LAN host, a self-hosted inference
server: `KTCONF_DEMO_LOCAL_BASE_URL`, `KTCONF_DEMO_LOCAL_MODEL`, optional
`KTCONF_DEMO_LOCAL_API_KEY`. The demo declares this provider LOCAL by
operator assertion, never by URL, so a public cloud API must not be used
here. Verified against gemma-4-12b-it:q5_k_m and gemma4:e4b (Ollama).

**How do I know the stage laptop runs the verified code?**
The freeze tag (`ktconf-2026-demo-v3`) records the known-good combination:
conference repo SHA + TramAI submodule SHA + tested models. `./scripts/preflight`
verifies the pin and the deterministic suite; `./scripts/preflight-real`
verifies the live model path; `./scripts/stage-up` verifies every instance's
profile over `/healthz`. The Git checkout itself establishes identity: start
conference morning from
`git checkout ktconf-2026-demo-v3 && git submodule update --init --recursive --checkout`.
