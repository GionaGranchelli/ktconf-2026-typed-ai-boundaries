export const boundaries = [
  {
    id: 'LOCAL_NVIDIA',
    title: 'LOCAL',
    subtitle: 'Private compute boundary',
    stack: ['NVIDIA RTX', 'llama.cpp', 'Nemotron 3 Nano'],
  },
  {
    id: 'EU_CLOUD',
    title: 'EU CLOUD',
    subtitle: 'Regional cloud boundary',
    stack: ['Nebius · France', 'NVIDIA H200', 'NVIDIA NIM · Nemotron'],
  },
  {
    id: 'GLOBAL_CLOUD',
    title: 'GLOBAL',
    subtitle: 'Global hosted boundary',
    stack: ['Build.NVIDIA.com', 'NVIDIA hosted inference', 'Nemotron'],
  },
]

export function allowedBoundaries(metadata) {
  if (!metadata) return new Set()
  const classification = metadata.classification
  const residency = metadata.residency

  if (classification === 'RESTRICTED' || residency === 'LOCAL_ONLY') {
    return new Set(['LOCAL_NVIDIA'])
  }
  if (classification === 'CONFIDENTIAL' || residency === 'EU_ONLY') {
    return new Set(['LOCAL_NVIDIA', 'EU_CLOUD'])
  }
  return new Set(['LOCAL_NVIDIA', 'EU_CLOUD', 'GLOBAL_CLOUD'])
}

export function routeToBoundary(route) {
  if (route === 'LOCAL_NVIDIA') return 'LOCAL_NVIDIA'
  if (route === 'EU_CLOUD') return 'EU_CLOUD'
  if (route === 'GLOBAL_CLOUD') return 'GLOBAL_CLOUD'
  return null
}

export function formatMoney(amountCents, currency = 'EUR') {
  if (amountCents === undefined || amountCents === null) return '—'
  return new Intl.NumberFormat('en', { style: 'currency', currency }).format(amountCents / 100)
}

export function counterDelta(before, after, key) {
  if (!before || !after) return null
  return (after[key] ?? 0) - (before[key] ?? 0)
}
