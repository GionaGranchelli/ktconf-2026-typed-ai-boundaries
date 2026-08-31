export class ApiError extends Error {
  constructor(message, status, body) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { Accept: 'application/json', ...(options.headers || {}) },
  })
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message = typeof body === 'object'
      ? body.message || body.reason || body.error || body.code || `HTTP ${response.status}`
      : String(body) || `HTTP ${response.status}`
    throw new ApiError(message, response.status, body)
  }

  return body
}

export function health() {
  return request('/governance/healthz')
}

export function getStats() {
  return request('/governance/stats')
}

export function analyzePdf(file) {
  const data = new FormData()
  data.append('file', file)
  return request('/invoices/analyze-pdf', {
    method: 'POST',
    body: data,
  })
}

export function approve(approvalId) {
  return request(`/approvals/${encodeURIComponent(approvalId)}/approve`, { method: 'POST' })
}

export function deny(approvalId) {
  return request(`/approvals/${encodeURIComponent(approvalId)}/deny`, { method: 'POST' })
}

export function getEvidence(approvalId) {
  return request(`/approvals/${encodeURIComponent(approvalId)}/evidence`)
}

/**
 * DEMO-ONLY boundary proof: send RESTRICTED data to the cloud operation.
 * TramAI must deny it before any provider is invoked (HTTP 403).
 * api.js will throw ApiError with status 403 — the caller must catch it.
 * The cloud invocation counter must not change (delta = 0).
 */
export function attemptForbiddenRoute(classification = 'RESTRICTED') {
  return request('/invoices/boundary/restricted-cloud', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      classification,
      invoice: {
        invoiceId: 'PROOF-DENY-001',
        supplierName: 'TramAI Policy Verifier',
        amountCents: 42830,
        currency: 'EUR',
        description: 'Boundary proof: TramAI must deny this before cloud invocation',
      },
    }),
  })
}

