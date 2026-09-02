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

export function analyzePdf(file, forceRoute = null) {
  const data = new FormData()
  data.append('file', file)
  const query = forceRoute ? `?forceRoute=${encodeURIComponent(forceRoute)}` : ''
  return request(`/invoices/analyze-pdf${query}`, {
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

export function reissueApproval(approvalId) {
  return request(`/approvals/${encodeURIComponent(approvalId)}/reissue`, { method: 'POST' })
}

export function getEvidence(approvalId) {
  return request(`/approvals/${encodeURIComponent(approvalId)}/evidence`)
}

export function getDocumentHistory() {
  return request('/governance/documents')
}

export function getDocumentHistoryRecord(id) {
  return request(`/governance/documents/${encodeURIComponent(id)}`)
}

/**
 * DEMO-ONLY boundary proof: send a CONFIDENTIAL/EU_ONLY invoice to GLOBAL_CLOUD.
 * TramAI must deny it before any provider is invoked (HTTP 403).
 * api.js will throw ApiError with status 403 — the caller must catch it.
 * The cloud invocation counter must not change (delta = 0).
 */
export function attemptForbiddenRoute(file) {
  if (!file) {
    throw new Error('Select a CONFIDENTIAL/EU_ONLY or RESTRICTED/LOCAL_ONLY PDF first.')
  }
  return analyzePdf(file, 'GLOBAL_CLOUD')
}
