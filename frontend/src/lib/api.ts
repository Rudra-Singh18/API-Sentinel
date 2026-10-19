import type { AccountSettings, CheckResult, Incident, Monitor, MonitorInput, PerformancePoint, PublicStatus, StatusPage, User } from '../types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE !== 'false'
let cachedCsrfToken: string | null = null

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers.set('X-XSRF-TOKEN', await csrfToken())
  const response = await fetch(`${API_BASE}${path}`, { ...init, method, credentials: 'include', headers })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { detail?: string; message?: string } | null
    throw new Error(body?.detail ?? body?.message ?? `Request failed (${response.status})`)
  }
  if (response.status === 204 || response.status === 202) return undefined as T
  return response.json() as Promise<T>
}

async function csrfToken(): Promise<string> {
  if (cachedCsrfToken) return cachedCsrfToken
  let response: Response
  try {
    response = await fetch(`${API_BASE}/auth/csrf`, { credentials: 'include' })
  } catch {
    throw new Error('Cannot reach the API. Check that the backend is running and the /api proxy is configured.')
  }
  if (!response.ok) {
    throw new Error(`The API could not start a secure session (HTTP ${response.status}). Check the backend and database connection.`)
  }
  const body = await response.json() as { token: string }
  if (!body.token) throw new Error('The API did not return a CSRF token. Check the backend configuration.')
  cachedCsrfToken = body.token
  return cachedCsrfToken
}

const sampleKey = 'api-sentinel-demo-monitors'
function sampleMonitors(): Monitor[] {
  const saved = localStorage.getItem(sampleKey)
  if (saved) {
    try { return JSON.parse(saved) as Monitor[] } catch { localStorage.removeItem(sampleKey) }
  }
  const now = Date.now()
  return [
    { id: 'demo-payments', name: 'Payments API', url: 'https://api.acme.dev/health', method: 'GET', expectedStatus: 200, intervalSeconds: 60, timeoutMs: 5000, enabled: true, status: 'UP', latestResponseMs: 142, lastCheckedAt: new Date(now - 90000).toISOString(), uptimePercent: 99.98 },
    { id: 'demo-search', name: 'Search service', url: 'https://search.acme.dev/ping', method: 'GET', expectedStatus: 200, intervalSeconds: 300, timeoutMs: 5000, enabled: true, status: 'DEGRADED', latestResponseMs: 812, lastCheckedAt: new Date(now - 240000).toISOString(), uptimePercent: 99.71 },
    { id: 'demo-identity', name: 'Identity gateway', url: 'https://identity.acme.dev/health', method: 'GET', expectedStatus: 200, intervalSeconds: 60, timeoutMs: 5000, enabled: true, status: 'UP', latestResponseMs: 96, lastCheckedAt: new Date(now - 180000).toISOString(), uptimePercent: 100 },
    { id: 'demo-webhooks', name: 'Webhook receiver', url: 'https://hooks.acme.dev/ready', method: 'HEAD', expectedStatus: 200, intervalSeconds: 900, timeoutMs: 10000, enabled: true, status: 'PENDING', latestResponseMs: null, lastCheckedAt: null, uptimePercent: null },
  ]
}
function saveSample(monitors: Monitor[]) { localStorage.setItem(sampleKey, JSON.stringify(monitors)) }

export const api = {
  async me(): Promise<User> { return request<User>('/auth/me') },
  async requestAuthCode(email: string, intent: 'SIGN_IN' | 'SIGN_UP'): Promise<void> {
    return request<void>('/auth/request-code', { method: 'POST', body: JSON.stringify({ email, intent }) })
  },
  async verifyAuthCode(email: string, code: string, intent: 'SIGN_IN' | 'SIGN_UP'): Promise<User> {
    return request<User>('/auth/verify-code', { method: 'POST', body: JSON.stringify({ email, code, intent }) })
  },
  async logout(): Promise<void> { return request<void>('/auth/logout', { method: 'POST' }) },
  async getAccountSettings(): Promise<AccountSettings> { return request<AccountSettings>('/account') },
  async updateProfile(input: { name: string; timeZone: string }): Promise<AccountSettings> {
    return request<AccountSettings>('/account/profile', { method: 'PATCH', body: JSON.stringify(input) })
  },
  async updateNotifications(input: { incidentEmailsEnabled: boolean; recoveryEmailsEnabled: boolean }): Promise<AccountSettings> {
    return request<AccountSettings>('/account/notifications', { method: 'PATCH', body: JSON.stringify(input) })
  },
  async sendTestEmail(): Promise<void> { return request<void>('/account/notifications/test', { method: 'POST' }) },
  async listMonitors(): Promise<Monitor[]> {
    if (DEMO_MODE) return sampleMonitors()
    return request<Monitor[]>('/monitors')
  },
  async getMonitor(id: string): Promise<Monitor> {
    if (DEMO_MODE) {
      const monitor = sampleMonitors().find((item) => item.id === id)
      if (!monitor) throw new Error('Sample monitor not found.')
      return monitor
    }
    return request<Monitor>(`/monitors/${encodeURIComponent(id)}`)
  },
  async getMonitorChecks(id: string, page = 0, size = 50): Promise<CheckResult[]> {
    if (DEMO_MODE) return []
    return request<CheckResult[]>(`/monitors/${encodeURIComponent(id)}/checks?page=${page}&size=${size}`)
  },
  async updateMonitor(id: string, input: MonitorInput): Promise<Monitor> {
    if (DEMO_MODE) {
      const updated = sampleMonitors().map((item) => item.id === id
        ? { ...item, ...input, status: 'PENDING' as const, latestResponseMs: null, lastCheckedAt: null, uptimePercent: null }
        : item)
      saveSample(updated)
      const monitor = updated.find((item) => item.id === id)
      if (!monitor) throw new Error('Sample monitor not found.')
      return monitor
    }
    return request<Monitor>(`/monitors/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(input) })
  },
  async listIncidents(): Promise<Incident[]> {
    if (DEMO_MODE) return [{ id: 'demo-incident-1', monitorId: 'demo-search', monitorName: 'Search service', summary: 'Response time above 700 ms threshold', state: 'ACKNOWLEDGED', failureCategory: 'SLOW_RESPONSE', startedAt: new Date(Date.now() - 1000 * 60 * 31).toISOString() }, { id: 'demo-incident-2', monitorId: 'demo-identity', monitorName: 'Identity gateway', summary: 'Connection timeout', state: 'RESOLVED', failureCategory: 'TIMEOUT', startedAt: new Date(Date.now() - 1000 * 60 * 60 * 25).toISOString() }]
    return request<Incident[]>('/incidents')
  },
  async acknowledgeIncident(id: string): Promise<Incident> {
    return request<Incident>(`/incidents/${id}/acknowledge`, { method: 'POST' })
  },
  async createMonitor(input: MonitorInput): Promise<Monitor> {
    if (!DEMO_MODE) return request<Monitor>('/monitors', { method: 'POST', body: JSON.stringify(input) })
    const monitor: Monitor = { ...input, id: `demo-${crypto.randomUUID()}`, enabled: true, status: 'PENDING', latestResponseMs: null, lastCheckedAt: null, uptimePercent: null }
    saveSample([monitor, ...sampleMonitors()])
    return monitor
  },
  async setMonitorEnabled(id: string, enabled: boolean): Promise<void> {
    if (!DEMO_MODE) return request<void>(`/monitors/${id}/${enabled ? 'resume' : 'pause'}`, { method: 'POST' })
    saveSample(sampleMonitors().map((monitor) => monitor.id === id ? { ...monitor, enabled, status: enabled ? 'PENDING' : 'PAUSED' } : monitor))
  },
  async deleteMonitor(id: string): Promise<void> {
    if (!DEMO_MODE) return request<void>(`/monitors/${id}`, { method: 'DELETE' })
    saveSample(sampleMonitors().filter((monitor) => monitor.id !== id))
  },
  async getPerformance(): Promise<PerformancePoint[]> {
    if (DEMO_MODE) return Array.from({ length: 25 }, (_, index) => ({ label: `${String(index).padStart(2, '0')}:00`, responseMs: Math.round(165 + Math.sin(index * 0.78) * 33 + Math.cos(index * 0.31) * 22) }))
    return request<PerformancePoint[]>('/analytics/performance?range=24h')
  },
  async listStatusPages(): Promise<StatusPage[]> { return request<StatusPage[]>('/status-pages') },
  async createStatusPage(input: { title: string; slug: string; description: string; monitorIds: string[] }): Promise<StatusPage> {
    return request<StatusPage>('/status-pages', { method: 'POST', body: JSON.stringify(input) })
  },
  async setStatusPagePublished(id: string, published: boolean): Promise<StatusPage> {
    return request<StatusPage>(`/status-pages/${id}/${published ? 'publish' : 'unpublish'}`, { method: 'POST' })
  },
  async deleteStatusPage(id: string): Promise<void> { return request<void>(`/status-pages/${id}`, { method: 'DELETE' }) },
  async getPublicStatus(slug: string): Promise<PublicStatus> { return request<PublicStatus>(`/status/${encodeURIComponent(slug)}`) },
}
