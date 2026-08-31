# task-008 — Contest evidence and proof suite

Status: `REVIEW`
Track: H
Milestone: M7
Depends on: task-006, task-007
Blocks: task-009

## Objective

Turn the implementation into judge-visible, reproducible evidence rather than unsupported claims.

## Scope

Build a bounded proof suite covering:

- PUBLIC -> GLOBAL success;
- EU-confidential -> GLOBAL denied before invocation;
- EU-confidential -> Scaleway/Mistral EU success;
- RESTRICTED -> EU denied and GLOBAL denied;
- RESTRICTED -> LOCAL success;
- malformed/missing metadata -> fail closed, zero provider calls;
- high-risk payment -> suspension;
- approve once -> one execution;
- duplicate approval -> rejected;
- deny -> zero execution;
- audit-chain verification.

Include real-provider smoke evidence separately from deterministic regression evidence so CI is not network-dependent.

## Metrics

At minimum expose:

- per-provider invocation counts/deltas;
- policy denials by reason code;
- payment execution count;
- audit chain validity;
- route selected/authorized;
- model/provider/trust-zone identities.

Do not invent a benchmark percentage unless the sample set and methodology are committed and reproducible.

## Acceptance criteria

- [x] Every deterministic headline contest claim maps to a machine-observable proof.
- [x] Denied routes prove invocation delta `0`.
- [ ] Real-provider smoke results are captured without secrets/document contents.
- [x] Deterministic proof suite is repeatable offline.
- [x] Evidence output is safe to publish.
- [x] Claims-boundary docs are updated for all new GTC behavior.
- [x] A concise judge-facing proof table can be generated from the evidence.

## Handoff

Provide claim -> test/evidence mapping, commands, outputs, sample counts, limitations, and the final publish-safe evidence locations.

Implementation evidence: `scripts/gtc-evidence` runs the full deterministic
application suite and the 20-scenario rehearsal, then prints a concise proof
table while also selecting the named contest proof tests directly. The
publish-safe evidence index is `docs/gtc/evidence/README.md`; the combined
live runner is `scripts/gtc-real-boundaries-smoke`. Live results are not
inferred from the offline gate. Task-006 combined real PDF evidence and
task-007 real local-Qwen payment/audit evidence is recorded; Nemotron payment
is not claimed
availability.

Latest execution: `./scripts/gtc-evidence` passed, including the full suite,
named contest tests, and 20/20 rehearsal. The combined live runner was blocked
by missing sourced provider configuration before startup, and the local
payment smoke could not connect to `127.0.0.1:1234`; no real-provider claim is
made.
