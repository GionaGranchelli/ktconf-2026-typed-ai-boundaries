# task-010 — Final contest freeze and submission package

Status: `BLOCKED`
Track: J
Milestone: M8b
Depends on: task-009
Blocks: none

## Objective

Freeze the Golden Ticket branch into a judge-ready submission that maximizes the four official scoring dimensions without overstating any claim.

## Scope

- Review implementation against official contest criteria.
- Run all deterministic gates plus each real NVIDIA route smoke.
- Freeze model IDs, provider configuration, sample PDFs, scripts, evidence, and documentation.
- Update the branch landing page for a zero-context judge.
- Produce final architecture diagram/source, 60-second video script, publication copy, and submission checklist.
- Perform a secret/privacy review of git diff, logs, screenshots, video frames, and evidence artifacts.
- Tag/freeze the exact submission commit only after all gates pass.

## Judge-facing landing page must answer in under two minutes

1. What problem does this solve?
2. What is technically novel?
3. Which NVIDIA technologies are actually used?
4. Why does `LOCAL` vs `EU_CLOUD` vs `GLOBAL_CLOUD` matter?
5. What proof shows policy acts before provider invocation?
6. What proof shows the model cannot authorize its own high-risk side effects?
7. How can the judge reproduce it?
8. What does the project deliberately not claim?

## Acceptance criteria

- [ ] All tasks 001–009 are DONE or explicitly waived with documented rationale.
- [ ] All deterministic tests/preflight pass on the frozen revision.
- [ ] Real LOCAL, EU_CLOUD, and GLOBAL_CLOUD smoke proofs pass on the frozen revision.
- [ ] 60-second demo is recorded from the frozen revision.
- [ ] README/GTC docs match actual behavior exactly.
- [ ] Secret/privacy inspection is clean.
- [ ] No unsupported `sovereign`, compliance, residency, safety, or exactly-once claims remain.
- [ ] Submission links point to the exact public branch/tag/commit intended for judges.
- [ ] `docs/gtc/CURRENT-STATE.md` records the final freeze SHA and evidence summary.

## Handoff

Produce a final go/no-go report with rubric scoring, exact freeze SHA/tag, verified links, gate results, known limitations, publication copy location, and any last external submission step that cannot be performed from the repository.
