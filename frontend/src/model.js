/**
 * Pure presentation-layer model.
 *
 * IMPORTANT: allowedBoundaries() derives a *visual hint* from trusted metadata
 * for pre-flight UI rendering only. The actual TramAI authorization decision
 * lives on the backend. The UI MUST NOT present a derived visual as proof
 * that TramAI authorized execution — only backend counters/responses are evidence.
 */

/** The three canonical execution boundaries for this demo. */
export const boundaries = [
  {
    id: 'LOCAL_NVIDIA',
    title: 'LOCAL',
    subtitle: 'Private compute · no network egress',
    stack: ['NVIDIA RTX GPU', 'llama.cpp runtime', 'Nemotron 3 Nano 4B'],
    statsKey: 'localNvidiaInvocationCount',
    description:
      'Data stays on-device. Required for RESTRICTED classification or LOCAL_ONLY residency.',
  },
  {
    id: 'EU_CLOUD',
    title: 'EU CLOUD',
    subtitle: 'Regional cloud · EU data boundary',
    stack: ['Nebius AI Cloud · eu-west1', 'NVIDIA H200 SXM', 'NVIDIA NIM · Nemotron'],
    statsKey: 'euNvidiaInvocationCount',
    description:
      'Data stays within EU jurisdiction (France). Runs on NVIDIA H200 via Nebius. Required for CONFIDENTIAL + EU_ONLY.',
  },
  {
    id: 'GLOBAL_CLOUD',
    title: 'GLOBAL',
    subtitle: 'Hosted NVIDIA inference',
    stack: ['Build.NVIDIA.com API', 'integrate.api.nvidia.com', 'Nemotron 3.5 Lightning 30B'],
    statsKey: 'globalNvidiaInvocationCount',
    description:
      'Public / unrestricted data. Denied when residency requires EU or local processing.',
  },
]

/**
 * Derive which boundaries are *visually* allowed based on trusted metadata.
 * Presentation-only — backend TramAI policy is authoritative.
 */
export function allowedBoundaries(metadata) {
  if (!metadata) return new Set()
  const { classification, residency } = metadata

  if (classification === 'RESTRICTED' || residency === 'LOCAL_ONLY') {
    return new Set(['LOCAL_NVIDIA'])
  }
  if (classification === 'CONFIDENTIAL' || residency === 'EU_ONLY') {
    return new Set(['LOCAL_NVIDIA', 'EU_CLOUD'])
  }
  return new Set(['LOCAL_NVIDIA', 'EU_CLOUD', 'GLOBAL_CLOUD'])
}

/**
 * Map a backend route string to a boundary ID.
 * Returns null for legacy routes — never fabricate an NVIDIA mapping.
 */
export function routeToBoundary(route) {
  const map = {
    LOCAL_NVIDIA: 'LOCAL_NVIDIA',
    EU_CLOUD: 'EU_CLOUD',
    GLOBAL_CLOUD: 'GLOBAL_CLOUD',
  }
  return map[route] ?? null
}

/** Format cents as a currency string. */
export function formatMoney(amountCents, currency = 'EUR') {
  if (amountCents == null) return '—'
  return new Intl.NumberFormat('en-DE', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(amountCents / 100)
}

/** Compute counter delta between two stats snapshots. Returns null if either is absent. */
export function counterDelta(before, after, key) {
  if (!before || !after) return null
  return (after[key] ?? 0) - (before[key] ?? 0)
}

/** Map a backend risk string to a CSS pill class. */
export function riskPillClass(risk) {
  if (!risk) return 'pill--neutral'
  const r = risk.toUpperCase()
  if (r === 'HIGH') return 'pill--high'
  if (r === 'MEDIUM') return 'pill--medium'
  if (r === 'LOW') return 'pill--low'
  return 'pill--neutral'
}

/**
 * Policy matrix reference — mirrors docs/gtc/ARCHITECTURE.md.
 * Backend is authoritative; this is documentation-only visualization.
 */
export const policyMatrix = [
  {
    classification: 'PUBLIC',
    residency: 'ANY',
    LOCAL_NVIDIA: true,
    EU_CLOUD: true,
    GLOBAL_CLOUD: true,
  },
  {
    classification: 'CONFIDENTIAL',
    residency: 'EU_ONLY',
    LOCAL_NVIDIA: true,
    EU_CLOUD: true,
    GLOBAL_CLOUD: false,
  },
  {
    classification: 'RESTRICTED',
    residency: 'LOCAL_ONLY',
    LOCAL_NVIDIA: true,
    EU_CLOUD: false,
    GLOBAL_CLOUD: false,
  },
]

/**
 * Provider identity map — mirrors GovernanceTelemetry.kt InvoiceRoute.target().
 * Values match what the backend logs. Not fabricated.
 */
export const providerIdentity = {
  LOCAL_NVIDIA:  { logicalModel: 'local-nvidia-invoice-model',  provider: 'local-nvidia-provider',  trustZone: 'LOCAL',        infra: 'NVIDIA RTX GPU',             runtime: 'llama.cpp',          model: 'Nemotron 3 Nano 4B Q4_K_M' },
  EU_CLOUD:      { logicalModel: 'eu-nvidia-invoice-model',     provider: 'eu-nvidia-provider',     trustZone: 'EU_CLOUD',     infra: 'Nebius eu-west1 · H200 SXM', runtime: 'NVIDIA NIM',         model: 'Nemotron 3.5 Lightning 30B' },
  GLOBAL_CLOUD:  { logicalModel: 'global-nvidia-invoice-model', provider: 'global-nvidia-provider', trustZone: 'GLOBAL_CLOUD', infra: 'Build.NVIDIA.com API',        runtime: 'NVIDIA hosted inf',  model: 'Nemotron 3.5 Lightning 30B A3B' },
  CLOUD:         { logicalModel: 'cloud-invoice-model',         provider: 'cloud-provider',         trustZone: 'GLOBAL_CLOUD', infra: 'Deterministic (cloud)',       runtime: '—',                  model: '—' },
  LOCAL:         { logicalModel: 'local-invoice-model',         provider: 'local-provider',         trustZone: 'LOCAL',        infra: 'Deterministic (local)',       runtime: '—',                  model: '—' },
}

