# Scaleway EU_CLOUD smoke evidence

Status: REAL SMOKE PASS

Recorded against commit: `a540ba27f0b42b454030c0abeea63fa266da5317`
Provider: `eu-scaleway-provider`
Trust zone: `EU_CLOUD`
Deployment: Scaleway Generative APIs, European dedicated deployment
Region: fr-par / Paris, France
Model: `mistral/mistral-small-24b-instruct-2501:bf16`

The initial stale base URL returned HTTP 404. After correcting the configured
base URL to the deployment's `/v1` endpoint, the smoke produced:

```text
catalog: model available (mistral/mistral-small-24b-instruct-2501:bf16)
direct: HTTP 200 OpenAI-compatible response
application allowed: HTTP 200, typed InvoiceAssessment,
  selectedRoute=EU_CLOUD, invocation delta=1
application denied: HTTP 403 classification-routing-blocked,
  invocation delta=0
```

No API key, endpoint token, private URL, or customer document is included.
Mistral is the temporary EU model and is not NVIDIA, Nemotron, or NIM.
