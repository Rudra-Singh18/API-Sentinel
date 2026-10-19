export type MonitorStatus = 'PENDING' | 'UP' | 'DEGRADED' | 'DOWN' | 'PAUSED'
export type Monitor = {
  id: string
  name: string
  url: string
  method: 'GET' | 'HEAD'
  expectedStatus: number
  intervalSeconds: number
  timeoutMs: number
  failureThreshold?: number
  recoveryThreshold?: number
  responseTimeThresholdMs?: number
  enabled: boolean
  status: MonitorStatus
  latestResponseMs: number | null
  lastCheckedAt: string | null
  uptimePercent: number | null
}
export type CheckResult = {
  id: string
  startedAt: string
  completedAt: string
  outcome: 'AVAILABLE' | 'UNAVAILABLE'
  httpStatus: number | null
  responseTimeMs: number | null
  failureCategory: string | null
  diagnosticMessage: string | null
}
export type MonitorInput = Pick<Monitor, 'name' | 'url' | 'method' | 'expectedStatus' | 'intervalSeconds' | 'timeoutMs'> & { failureThreshold: number; recoveryThreshold: number; responseTimeThresholdMs: number }
export type Incident = { id: string; monitorId: string; monitorName: string; summary: string; state: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'; failureCategory: string | null; startedAt: string }
export type PerformancePoint = { label: string; responseMs: number | null }
export type User = { id: string; name: string; email: string; createdAt: string }
export type AccountSettings = {
  id: string
  name: string
  email: string
  timeZone: string
  incidentEmailsEnabled: boolean
  recoveryEmailsEnabled: boolean
  emailConfigured: boolean
  createdAt: string
}
export type StatusPage = { id: string; title: string; slug: string; description: string; published: boolean; updatedAt: string; monitors: { id: string; name: string; status: MonitorStatus }[] }
export type PublicStatus = { title: string; description: string; overallStatus: MonitorStatus; generatedAt: string; services: { name: string; status: MonitorStatus; uptimePercent: number | null; lastCheckedAt: string | null }[]; incidents: { monitorName: string; state: string; startedAt: string; resolvedAt: string | null }[] }
