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
