# LOCAL NVIDIA smoke evidence

Status: REAL SMOKE PASS

Provider: `local-nvidia-provider`
Trust zone: `LOCAL`
Hardware: NVIDIA RTX 3060
Runtime: llama.cpp 9986, driver 580.173.02
Model: NVIDIA Nemotron 3 Nano 4B, GGUF Q4_K_M

The opt-in local smoke produced:

```text
health: HTTP 200
catalog: configured Nemotron model available
direct: HTTP 200 OpenAI-compatible response
application: HTTP 200, typed InvoiceAssessment,
  selectedRoute=LOCAL_NVIDIA, invocationCount=1
```

This is an individual real-provider inference proof. The combined real PDF
boundary run is owned by task-008 and remains separate from deterministic CI.
No credentials, endpoint identifiers, customer data, or secret-bearing output
is included.
