# task-014 — Opt-in governed interaction capture

Status: **PLANNED**  
Track: Post-freeze / data quality  
Owner branch: `contest/gtc-nvidia-submission`  
Depends on: task-006, task-007, task-008, task-012

## Objective

Capture sanitized, structured provider interactions so approved examples can be
reviewed and later exported for fine-tuning or evaluation, without changing
TramAI governance, routing, approval, audit, or invocation-counter semantics.

This is a post-freeze task. It must not block the GTC submission path.

## Proposed capture seam

Reuse the existing provider composition with one opt-in decorator:

```text
OpenAiCompatibleProvider
  -> ModelAliasProvider
  -> InteractionRecordingProvider
  -> CountingModelProvider
```

The recorder should capture the logical operation and actual provider/model,
the request/response message sequence, structured output, tool calls/results,
validation outcome, retry/attempt metadata, latency, and workflow correlation
identifiers. It must not implement policy or decide whether a call is allowed.

## Scope

- Add an opt-in recorder at the existing provider boundary.
- Write versioned, append-only sanitized JSONL records locally.
- Capture message roles and content, assistant tool calls, tool results,
  structured output, provider outcome, and relevant timing/token metadata.
- Preserve multi-turn tool workflows, including suspension and resumed calls.
- Label deterministic versus real-provider records.
- Provide a separate reviewed export/curation step for fine-tuning JSONL.
- Keep governance-denied requests out of provider interaction records because no
  provider call occurred; retain only safe policy evidence if needed.

## Data-minimization rules

- `KTCONF_CAPTURE_ENABLED` defaults to `false`.
- Never persist API keys, authorization headers, endpoint tokens, or secrets.
- Do not persist PDF bytes or raw documents by default.
- Do not persist hidden chain-of-thought/reasoning traces. An explicit
  user-visible rationale may be retained only under the selected redaction
  profile.
- Hash or remove invoice identifiers and redact descriptions unless an explicit
  synthetic-data profile permits them.
- Fine-tuning export requires human review; capture is not automatic training.

## Non-goals

- No new model/provider or HTTP client.
- No automatic fine-tuning, hosted dataset, or cloud upload.
- No database, dashboard, or multi-tenant retention system in the first pass.
- No provider-specific governance or model tool-choice policy.
- No weakening of deterministic-stage environment isolation.

## Acceptance criteria

- [ ] Capture is disabled by default and deterministic tests remain unchanged.
- [ ] A captured record has a version, operation, provider/model, trust zone,
      correlation ID, ordered messages, outcome, and redaction profile.
- [ ] Structured output and validation failures are represented without secrets.
- [ ] Assistant tool calls, tool results, suspension, resume, and final output
      remain distinguishable in the ordered record.
- [ ] A TramAI policy denial produces no provider interaction record.
- [ ] Raw credentials, PDF bytes, and hidden chain-of-thought are excluded by
      tests or an explicit sanitizer assertion.
- [ ] A reviewed export produces the selected fine-tuning JSONL format without
      changing the source capture records.
- [ ] Retention and deletion behavior are documented.

## Evidence required

Record deterministic tests for disabled capture, one typed interaction, one
tool-call continuation, structured-output rejection, policy denial with no
provider record, redaction, and reviewed export. Include known limitations:
JSONL is a local demo store until retention, concurrency, access control, and
durable production storage are designed.
