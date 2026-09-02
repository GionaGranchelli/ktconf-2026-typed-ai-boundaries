# Scaleway serverless smoke evidence

Date: 2026-09-02
Source revision: working tree checkpoint for task-016

This artifact records the live opt-in smoke for the temporary EU execution
boundary. It intentionally omits API keys, deployment identifiers, endpoint
URLs, prompts, and document contents.

| Check | Result |
|---|---|
| Service | Scaleway Generative APIs serverless |
| Deployment location | Operator-configured Scaleway deployment; no region claim is made by this artifact |
| Model | `mistral-medium-3.5-128b` |
| Protocol | OpenAI-compatible `/v1` |
| `/v1/models` | HTTP 200; configured model advertised |
| Direct chat completion | HTTP 200 |
| Typed application route | HTTP 200; `selectedRoute=EU_CLOUD` |
| Allowed provider delta | EU provider `+1` |
| Forced restricted-EU route | HTTP 403; `classification-routing-blocked` |
| Denied provider delta | EU provider `0` |

The serverless provider is temporary EU infrastructure and is not NVIDIA,
Nemotron, or NIM. The smoke was run with the API key supplied through the
operator environment; the key was not printed or persisted in this artifact.
