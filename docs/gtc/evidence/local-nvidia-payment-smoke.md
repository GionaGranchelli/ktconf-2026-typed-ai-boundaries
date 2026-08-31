# LOCAL NVIDIA payment smoke

This is the real governed action proof for task-007. It uses the local NVIDIA
execution boundary with Qwen as the action-capable model.

```text
Command:
KTCONF_GTC_LOCAL_NVIDIA_MODEL=qwen/qwen3.8-27b \
  ./scripts/gtc-local-nvidia-payment-smoke

Model catalog: reachable
Application: HTTP 202, LOCAL_NVIDIA schedule-payment suspended, payment count=0
Approval: payment count 0 -> 1
Duplicate approval: HTTP 409, payment count remains 1
Audit evidence: audit chain valid with approval lifecycle
```

The application used the real local endpoint at `127.0.0.1:1234`; credentials
were not required or recorded. The request entered through
`payment-local-invoice.pdf` with `RESTRICTED` and `LOCAL_ONLY` metadata. The
hardware/model deployment is local NVIDIA RTX with Qwen
`qwen/qwen3.8-27b`.

Nemotron `nvidia/nemotron-3-nano` and `nvidia/nemotron-3-nano-4b` were also
evaluated. They returned typed high-risk assessments but did not reliably emit
the tool call under the combined typed-output/tool contract, so this artifact
makes no Nemotron payment claim.
