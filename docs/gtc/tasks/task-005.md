# task-005 — Real PDF + trusted metadata ingestion

Status: `READY`
Track: E
Milestone: M4
Depends on: none
Blocks: task-006

## Objective

Replace the contest's JSON-only entry point with a real synthetic PDF document whose trusted classification/residency metadata is parsed locally before any provider invocation.

## Scope

- Define a minimal, documented metadata contract suitable for synthetic demo documents.
- Prefer embedded PDF metadata (for example XMP/custom properties) if implementation remains simple and robust; a signed/trusted sidecar/envelope is acceptable if better justified.
- Parse metadata before document content is handed to any model/provider.
- Fail closed when required metadata is absent, malformed, unsupported, or contradictory.
- Extract/prepare document content only after routing authorization.
- Produce at least three synthetic demo documents: PUBLIC, CONFIDENTIAL+EU, RESTRICTED.
- Ensure documents contain no real personal/company-sensitive information.

## Security ordering invariant

```text
receive bytes
  -> parse trusted metadata locally
  -> validate classification/residency
  -> ask TramAI whether proposed boundary is allowed
  -> only then expose document content to that provider
```

Tests must prove provider invocation count remains unchanged on metadata/routing rejection.

## Non-goals

- Do not add probabilistic classification and call it authoritative.
- Do not claim Microsoft Purview or another external labeling standard unless actually implemented.
- Do not OCR/send content before the placement decision.

## Acceptance criteria

- [ ] Real PDF upload/ingestion path exists.
- [ ] Trusted metadata contract is documented.
- [ ] Metadata parsing is local and precedes provider invocation.
- [ ] Missing/malformed metadata fails closed.
- [ ] Three synthetic documents cover PUBLIC, EU-confidential, and RESTRICTED cases.
- [ ] Tests prove zero provider invocation on rejected metadata/route cases.
- [ ] Existing JSON API may remain for KTConf compatibility.

## Handoff

Document metadata schema, parser, endpoint/input contract, sample documents, test matrix, changed files, and exact proof that no provider sees bytes before authorization.
