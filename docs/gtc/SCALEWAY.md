# Scaleway EU_CLOUD deployment

This is the active temporary EU provider path for task-004. Scaleway
Generative APIs provides an OpenAI-compatible serverless endpoint for a
European deployment running the configured Mistral model. Mistral is not
NVIDIA, Nemotron, or NIM.

## Configuration

Set these values outside git:

```text
KTCONF_GTC_EU_SCALEWAY_BASE_URL=https://api.scaleway.ai/<deployment-id>/v1
KTCONF_GTC_EU_SCALEWAY_API_KEY=<Scaleway API key>
KTCONF_GTC_EU_SCALEWAY_MODEL=mistral-medium-3.5-128b
```

The application also accepts `SCW_BASE_URL`, `SCW_MODEL`, and
`SCW_SECRET_KEY` through the smoke script. `SCW_API_KEY` remains a backwards-
compatible fallback. Authentication is `Authorization: Bearer`.
The base URL is configuration, not a production constant, and the model ID
must be verified from `GET /v1/models`.

For Docker, set the namespaced variables before recreating the container so an
old dedicated `SCW_BASE_URL` cannot be reused accidentally:

```bash
export KTCONF_GTC_EU_SCALEWAY_BASE_URL='https://api.scaleway.ai/<deployment-id>/v1'
export KTCONF_GTC_EU_SCALEWAY_MODEL='mistral-medium-3.5-128b'
export KTCONF_GTC_EU_SCALEWAY_API_KEY="$SCW_SECRET_KEY"
docker compose up -d --build --force-recreate ktconf
```

## Smoke

```bash
source ~/.zshrc
KTCONF_GTC_EU_SCALEWAY_BASE_URL=https://api.scaleway.ai/<deployment-id>/v1 \
KTCONF_GTC_EU_SCALEWAY_MODEL=mistral-medium-3.5-128b \
KTCONF_GTC_EU_SCALEWAY_API_KEY="$SCW_SECRET_KEY" \
./scripts/gtc-eu-scaleway-smoke
```

The script performs catalog validation, direct chat completion, typed
application analysis through `POST /invoices/eu-scaleway`, and the deliberate
`RESTRICTED -> EU_CLOUD` denial proof. It expects the real endpoint to support
the existing structured-output contract; a typed application HTTP 200 is the
authoritative structured-output proof. It never prints the API key.

## Governance and limitations

The provider is declared `EU_CLOUD` in TramAI configuration. TramAI authorizes
the trust zone before provider invocation; Scaleway-specific authorization is
not implemented in the controller or service. With no real endpoint/key,
this identity uses the deterministic fixture provider, preserving offline
tests. No real Scaleway smoke result is claimed until the script passes.

This temporary serverless Mistral deployment unblocks the EU execution-boundary
integration but does not satisfy the original NVIDIA/Nemotron EU technology
claim. Serverless availability, cold starts, quotas, and usage cost are
provider-managed and may vary; this is not a dedicated-capacity guarantee. A
later managed EU Nemotron deployment can replace the configured base URL, API
key, and model ID without changing `EU_CLOUD` governance. If the replacement
is not OpenAI-compatible, only the provider adapter composition would need to
change; routing and policy remain unchanged.

## Historical Nebius record

The abandoned Nebius experiments remain in
[`NVIDIA-NEBIUS.md`](NVIDIA-NEBIUS.md) and `CURRENT-STATE.md`: repeated
Serverless `code=13`, H200/H100 capacity failures with `code=8`, and no
successful inference response. Nebius is not debugged further for P0.
