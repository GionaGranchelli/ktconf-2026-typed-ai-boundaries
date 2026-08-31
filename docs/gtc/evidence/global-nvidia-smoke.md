# GLOBAL NVIDIA smoke evidence

Status: REAL SMOKE PASS

Recorded against commit: `8f6c9c63196e4cab7698ed8a23a0113605e43db3`
Provider: `global-nvidia-provider`
Trust zone: `GLOBAL_CLOUD`
Endpoint: Build.NVIDIA.com hosted OpenAI-compatible API
Model: `nvidia/nemotron-3.5-lightning-30b-a3b`

The opt-in smoke, run from a fresh zsh after sourcing the operator's local
environment, produced:

```text
catalog: model available (nvidia/nemotron-3.5-lightning-30b-a3b)
direct: HTTP 200
application: HTTP 200, typed InvoiceAssessment,
  selectedRoute=GLOBAL_CLOUD, invocationCount=1
denial: HTTP 403 before provider invocation, counter delta=0
```

No API key, token, customer document, or secret-bearing output is included.
