# Combined real three-boundary proof

Execution date: 2026-08-31
Application HEAD at execution: `6a8f040a6f54820aab43add0700f8b353b198911`

The run used one Spring process and these configured deployments:

| Input | Boundary | Actual model | Result |
|---|---|---|---|
| `PUBLIC / ANY` PDF | `GLOBAL_CLOUD` | `nvidia/nemotron-3.5-lightning-30b-a3b` | HTTP 200, invocation delta +1 |
| `CONFIDENTIAL / EU_ONLY` PDF | `EU_CLOUD` | `mistral/mistral-small-24b-instruct-2501:bf16` | HTTP 200, EU delta +1 |
| `RESTRICTED / LOCAL_ONLY` PDF | `LOCAL_NVIDIA` | `qwen/qwen3.8-27b` | HTTP 200, local delta +1 |

Denial proofs in the same run:

```text
CONFIDENTIAL/EU_ONLY forced GLOBAL_CLOUD
  HTTP 403, classification-routing-blocked, global invocation delta 0

RESTRICTED/LOCAL_ONLY forced EU_CLOUD
  HTTP 403, classification-routing-blocked, EU invocation delta 0

RESTRICTED/LOCAL_ONLY forced GLOBAL_CLOUD
  HTTP 403, classification-routing-blocked, global invocation delta 0
```

Command:

```text
./scripts/gtc-real-boundaries-smoke
```

The runner completed successfully. No prompts, document contents, keys, or
private endpoint identifiers are included here. The restricted local fixture
was adjusted to `amountCents=4200` so the combined typed-boundary proof does
not enter the separate high-value payment workflow; the €18,400 payment
fixture remains unchanged and is covered by the local Qwen payment artifact.
