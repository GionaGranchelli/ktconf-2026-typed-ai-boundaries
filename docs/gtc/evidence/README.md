# GTC evidence index

The repository separates repeatable offline evidence from opt-in live-provider
evidence. The offline gate never needs a GPU, network endpoint, or credential:

```bash
./scripts/gtc-evidence
```

It runs the full application test suite and the 20-scenario deterministic
rehearsal, then prints the judge-facing proof table. The assertions behind the
table include selected route, trust-zone authorization, provider invocation
deltas, payment count, duplicate approval rejection, and audit-chain validity.

## Live evidence

These are separately executed and must not be inferred from the deterministic
gate:

| Boundary/workflow | Command | Current status |
|---|---|---|
| LOCAL NVIDIA typed inference | `scripts/gtc-local-nvidia-smoke` | Individual proof recorded; combined PDF proof pending |
| EU Scaleway/Mistral typed inference | `scripts/gtc-eu-scaleway-smoke` | Individual proof recorded |
| GLOBAL NVIDIA typed inference | `scripts/gtc-global-nvidia-smoke` | Individual proof recorded |
| LOCAL NVIDIA payment PDF | `scripts/gtc-local-nvidia-payment-smoke` | Pending local endpoint availability |
| Combined three-PDF run | task-008 evidence run | Pending |

Live evidence artifacts must record only provider identity, region/endpoint
type, model ID, HTTP result, selected route, safe counters, and limitations.
Never record API keys, bearer tokens, endpoint UUIDs, account identifiers, or
document contents.
