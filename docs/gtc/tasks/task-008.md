# task-008 — Contest evidence and proof suite

Status: `BLOCKED`
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

- [ ] Every headline contest claim maps to a machine-observable proof.
- [ ] Denied routes prove invocation delta `0`.
- [ ] Real-provider smoke results are captured without secrets/document contents.
- [ ] Deterministic proof suite is repeatable offline.
- [ ] Evidence output is safe to publish.
- [ ] Claims-boundary docs are updated for all new GTC behavior.
- [ ] A concise judge-facing proof table can be generated from the evidence.

## Handoff

Provide claim -> test/evidence mapping, commands, outputs, sample counts, limitations, and the final publish-safe evidence locations.
