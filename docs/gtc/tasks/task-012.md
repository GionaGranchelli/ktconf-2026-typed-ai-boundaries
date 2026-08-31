# task-012 — TramAI-native provenance and sovereign evidence

Status: `READY`
Track: K
Milestone: M8
Depends on: task-005
Blocks: —

## Objective

Expose the TramAI-native governance evidence already present in the
application so the GTC submission demonstrates not only governed behavior,
but also classification provenance and the runtime's sovereign configuration.

The application must continue to distinguish:

```text
manual/JSON request       -> ClassificationSource.DECLARED
trusted PDF rule metadata -> ClassificationSource.RULE_BASED
```

and must use TramAI's native sovereign evidence APIs where the pinned starter
supports them without manually constructing a second runtime.

## Scope

### P1 — classification provenance

- Preserve `ClassificationSource.RULE_BASED` when trusted PDF metadata creates
  the `ClassifiedDocument`.
- Preserve `DECLARED` for ordinary JSON/manual requests.
- Add deterministic assertions and visible, secret-free evidence for both
  sources.
- Keep the trusted PDF metadata-before-egress invariant unchanged.

### P1 — native sovereign evidence pack

- Locate the pinned TramAI `SovereignEvidencePack`/`SovereignEvidencePackV1`
  integration and its Spring composition path.
- Emit or expose one publish-safe TramAI-native evidence pack for the contest
  runtime, including configured models, providers, trust zones, policy and
  audit-chain evidence where supported.
- Reuse the existing `SovereignTramaiRuntime` and starter-managed beans; do
  not construct a second sovereign runtime in application code.
- Keep the existing task-008 deterministic evidence table as a behavior-level
  index, while clearly identifying the native TramAI pack as runtime/config
  evidence.

### P2 — bounded optional improvements

- Assess `ModelArtifactVerifier`/verification receipts for the local Nemotron
  artifact. Implement only if the pinned Spring integration is direct and
  testable; otherwise document the exact limitation and follow-on change.
- Remove provider/trust-zone duplication from application telemetry only if
  TramAI observer/evidence output already supplies equivalent authoritative
  fields without reducing current route-selection visibility.

## Non-goals

- Do not redesign route selection; the application proposal and TramAI policy
  authorization remain separate and explicit.
- Do not repin TramAI or create an upstream change without a separate task.
- Do not add approval timeout handling here.
- Do not make live providers, GPUs, artifact downloads, or credentials normal
  test dependencies.
- Do not claim cryptographic PDF signing, legal sovereignty, compliance, or
  artifact verification unless the exact verification actually passes.

## Acceptance criteria

- [ ] PDF-derived `ClassifiedDocument` retains `RULE_BASED` provenance.
- [ ] JSON/manual requests retain `DECLARED` provenance.
- [ ] Deterministic tests prove both provenance paths and remain offline.
- [ ] A TramAI-native sovereign evidence pack is emitted through the existing
  runtime composition, or the pinned-version limitation is documented with a
  concrete blocked interface.
- [ ] The pack contains no secrets, prompts, document contents, stack traces,
  or filesystem paths.
- [ ] Local Nemotron artifact verification is implemented only if cleanly
  supported; otherwise its limitation is recorded without weakening trust.
- [ ] Application telemetry does not duplicate authoritative provider/zone
  values unless the duplication is explicitly documented as proposal vs
  runtime evidence.
- [ ] Existing task-008 evidence, deterministic suite, and rehearsal remain
  green.

## Verification

Run the focused provenance/native-evidence tests, the full application suite,
`./scripts/gtc-evidence`, and secret/diff hygiene checks. If a live provider is
used to populate the native pack, record only sanitized provider identity,
model ID, trust zone, and verification result.

## Handoff

State the exact TramAI APIs and pinned revision used, changed files, provenance
assertions, evidence-pack location/schema, artifact-verification result or
limitation, telemetry decision, tests/gates, and any follow-on blocker before
task-009.
