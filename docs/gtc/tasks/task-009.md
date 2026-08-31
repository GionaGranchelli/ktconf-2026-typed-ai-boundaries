# task-009 — `gtc-demo` and 60-second recording flow

Status: `BLOCKED`
Track: I
Milestone: M8a
Depends on: task-008
Blocks: task-010

## Objective

Create one bounded, rehearsable command flow that produces the exact visible proof needed for the Golden Ticket video without exposing secrets or requiring the viewer to understand the whole repository.

## Scope

- Add `scripts/gtc-demo` (or equivalent) with explicit scenario subcommands.
- Optimize output for screen recording: classification/residency, proposed route, policy decision, provider/model/zone, counter delta, approval state, payment count, audit validity.
- Keep raw prompts, document content, tokens, and credentials out of output.
- Provide a 60-second shot list and a slower technical rehearsal mode.
- Add preflight checks for required real-provider configuration, with actionable fail-closed messages.
- Rehearse fallback behavior if one remote endpoint is unavailable; do not fake a real-provider success.

## Target 60-second sequence

1. Drop/open EU-confidential PDF.
2. Show trusted metadata read locally.
3. Propose GLOBAL -> TramAI DENIED -> GLOBAL delta 0.
4. Route to EU_CLOUD -> Scaleway/Mistral success (temporary provider).
5. Show RESTRICTED -> LOCAL only.
6. Show the local NVIDIA Qwen action model proposes €18,400 payment.
7. TramAI suspends -> human approve -> payment 0 -> 1.
8. Show duplicate reject/audit `VALID` as final proof.
9. End card: `The model reasons. TramAI governs.`

## Acceptance criteria

- [ ] A fresh operator can run the documented demo sequence.
- [ ] Output is legible at recording resolution.
- [ ] No secrets or sensitive content appear on screen/logs.
- [ ] Core sequence is recordable in ~60 seconds after providers are warm.
- [ ] A technical/full proof mode remains available outside the 60-second cut.
- [ ] Failure messages are deterministic and do not silently fall back to simulated inference.
- [ ] Rehearsal is run multiple times successfully before freeze.

## Handoff

Provide the final commands, expected output markers, timing observations, recording shot list, warm-up requirements, failure/fallback procedure, and any visual cleanup still needed.
