# GTC Governance Console

Vue 3 + Vite frontend for the NVIDIA GTC Berlin Golden Ticket demo.

## Purpose

This UI visualizes backend governance evidence. It is intentionally not a second policy engine.

It currently binds to:

- `GET /governance/healthz`
- `GET /governance/stats`
- `POST /invoices/analyze-pdf`
- `POST /approvals/{approvalId}/approve`
- `POST /approvals/{approvalId}/deny`
- `GET /approvals/{approvalId}/evidence`

## Development

Start the Spring backend on port 8080, then:

```bash
./scripts/gtc-ui-dev
```

Vite runs on `http://localhost:5173` and proxies API requests to Spring. Override the backend with `GTC_BACKEND_URL` if needed.

## Production build

```bash
./scripts/gtc-ui-build
```

Vite writes the generated app to:

```text
app/src/main/resources/static/gtc/
```

Generated assets are ignored by git. Build the UI before packaging the Spring Boot jar when the embedded console is required.

## Guardrails

- No credential or provider secret belongs in the frontend.
- No classification or route result may be invented in browser state and presented as TramAI evidence.
- The UI may visualize the expected policy matrix from trusted metadata, but the backend-selected route and provider counters remain the proof.
- Legacy `LOCAL` / `CLOUD` routes are shown explicitly until task-006 completes the three-NVIDIA-boundary PDF flow.

## Toolchain

- Vue 3
- Vite 8
- no router
- no state library
- no UI/component framework

Node must satisfy Vite 8's runtime requirement (`>=20.19.0`; current Node 22 LTS also works when sufficiently recent).
