# Documentation index

One audience each — start where you are.

| Document | Audience | Read when |
|---|---|---|
| [../README.md](../README.md) | Everyone (QR code) | You want the whole demo in 2 minutes: scenarios, quick start, pinned revision |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Curious engineers | You want to see how the typed boundary, policy routing, approval and audit are wired to real TramAI |
| [DEMO-SCRIPT.md](DEMO-SCRIPT.md) | The speaker | You are on stage, or running the conference-morning freeze check |
| [FAILURE-RECOVERY.md](FAILURE-RECOVERY.md) | The speaker | Something on stage went wrong |
| [CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md) | Reviewers, skeptics | You want to know exactly what is real vs simulated, and how that was verified |
| [TRAMAI-INTEGRATION.md](TRAMAI-INTEGRATION.md) | Contributors | You want to change the pinned TramAI revision or the composite build |
| [UPSTREAM-ENUM-SCHEMA-BUG.md](UPSTREAM-ENUM-SCHEMA-BUG.md) | Contributors | You want the full record of the enum-schema defect (tramAI #261/#262) this demo exposed |

## FAQ

**Is the demo fake?**
No, but it is honest about what is simulated. The deterministic scenarios use
a scripted model and an in-memory payment ledger. Everything around them —
structured output, policy routing, tool governance, approval/continuation,
audit/evidence — is real TramAI behavior. `typed --real` replaces the scripted
model with an actual LLM. See [CLAIMS-BOUNDARY.md](CLAIMS-BOUNDARY.md).

**Does it need internet?**
Only once. After `./scripts/preflight`, the deterministic demo runs fully
offline — airplane mode is the on-stage proof. `typed --real` needs your
model endpoint (local Ollama or any OpenAI-compatible API).

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
No — an in-memory ledger with idempotency keys. The *behavior* it demonstrates
(exactly-once, duplicate-resume rejection) is real; the money is not.

**Can I use my own model?**
Any OpenAI-compatible endpoint: `KTCONF_DEMO_LOCAL_BASE_URL`,
`KTCONF_DEMO_LOCAL_MODEL`, optional `KTCONF_DEMO_LOCAL_API_KEY`. Verified
against gemma-4-12b-it:q5_k_m and gemma4:e4b (Ollama) and deepseek-chat.

**How do I know the stage laptop runs the verified code?**
The `ktconf-2026-demo-v1` tag records the known-good combination: conference
repo SHA + TramAI submodule SHA + tested models. `./scripts/preflight` and
`./scripts/preflight-real` verify the current checkout against it.
