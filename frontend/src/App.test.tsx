import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const apiMock = vi.hoisted(() => ({
  me: vi.fn(),
  requestAuthCode: vi.fn(),
  verifyAuthCode: vi.fn(),
  logout: vi.fn(),
  listMonitors: vi.fn(),
  getMonitor: vi.fn(),
  getMonitorChecks: vi.fn(),
  updateMonitor: vi.fn(),
  listIncidents: vi.fn(),
  acknowledgeIncident: vi.fn(),
  createMonitor: vi.fn(),
  setMonitorEnabled: vi.fn(),
  deleteMonitor: vi.fn(),
  getPerformance: vi.fn(),
  listStatusPages: vi.fn(),
  createStatusPage: vi.fn(),
  setStatusPagePublished: vi.fn(),
  deleteStatusPage: vi.fn(),
  getPublicStatus: vi.fn(),
  getAccountSettings: vi.fn(),
  updateProfile: vi.fn(),
  updateNotifications: vi.fn(),
  sendTestEmail: vi.fn(),
}))

vi.mock('./lib/api', () => ({ api: apiMock, DEMO_MODE: false }))

const sampleUser = { id: 'user-1', name: 'Avery Morgan', email: 'avery@example.test', createdAt: '2026-01-01T00:00:00Z' }
const sampleSettings = {
  ...sampleUser, timeZone: 'UTC', incidentEmailsEnabled: true, recoveryEmailsEnabled: true, emailConfigured: false,
}
const sampleMonitor = {
  id: 'monitor-1', name: 'Payments API', url: 'https://api.example.test/health', method: 'GET' as const,
  expectedStatus: 200, intervalSeconds: 60, timeoutMs: 5000, enabled: true, status: 'UP' as const,
  latestResponseMs: 92, lastCheckedAt: '2026-01-01T00:00:00Z', uptimePercent: 99.9,
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></QueryClientProvider>)
}

describe('API Sentinel console', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.removeItem('api-sentinel-theme')
    document.documentElement.dataset.theme = 'light'
    apiMock.me.mockResolvedValue(sampleUser)
    apiMock.listMonitors.mockResolvedValue([sampleMonitor])
    apiMock.getMonitor.mockResolvedValue(sampleMonitor)
    apiMock.updateMonitor.mockResolvedValue(sampleMonitor)
    apiMock.getMonitorChecks.mockResolvedValue([{
      id: 'check-1', startedAt: '2026-01-01T00:00:00Z', completedAt: '2026-01-01T00:00:00Z',
      outcome: 'AVAILABLE', httpStatus: 200, responseTimeMs: 92, failureCategory: null, diagnosticMessage: null,
    }])
    apiMock.listIncidents.mockResolvedValue([])
    apiMock.acknowledgeIncident.mockResolvedValue({
      id: 'incident-1', monitorId: 'monitor-1', monitorName: 'Payments API', summary: 'Unexpected response',
      state: 'ACKNOWLEDGED', failureCategory: 'HTTP_STATUS', startedAt: '2026-01-01T00:00:00Z',
    })
    apiMock.listStatusPages.mockResolvedValue([])
    apiMock.createMonitor.mockResolvedValue(sampleMonitor)
    apiMock.createStatusPage.mockResolvedValue({ id: 'page-1', title: 'Acme Status', slug: 'acme-status', description: '', published: false, updatedAt: '2026-01-01T00:00:00Z', monitors: [] })
    apiMock.getPublicStatus.mockResolvedValue({ title: 'Acme Status', description: 'Customer services', overallStatus: 'UP', generatedAt: '2026-01-01T00:00:00Z', services: [{ name: 'Payments API', status: 'UP', uptimePercent: 99.5, lastCheckedAt: '2026-01-01T00:00:00Z' }], incidents: [] })
    apiMock.getPerformance.mockResolvedValue([])
    apiMock.getAccountSettings.mockResolvedValue(sampleSettings)
    apiMock.updateProfile.mockImplementation(async (input) => ({ ...sampleSettings, ...input }))
    apiMock.updateNotifications.mockImplementation(async (input) => ({ ...sampleSettings, ...input }))
    apiMock.sendTestEmail.mockResolvedValue(undefined)
    apiMock.requestAuthCode.mockResolvedValue(undefined)
    apiMock.verifyAuthCode.mockRejectedValue(new Error('That code is invalid or expired. Request a new code and try again.'))
  })

  it('shows a monitor status from the API response', async () => {
    renderAt('/app/monitors')
    expect(await screen.findByText('Payments API')).toBeInTheDocument()
    expect(screen.getByText('Up')).toBeInTheDocument()
    expect(screen.getByText('92 ms')).toBeInTheDocument()
  })

  it('submits validated monitor configuration from the create dialog', async () => {
    apiMock.listMonitors.mockResolvedValue([])
    renderAt('/app/monitors')
    fireEvent.click(await screen.findByRole('button', { name: 'Add monitor' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('e.g. Payments API'), { target: { value: 'Catalog API' } })
    fireEvent.change(screen.getByPlaceholderText('https://api.example.com/health'), { target: { value: 'https://catalog.example.test/health' } })
    fireEvent.click(screen.getByRole('button', { name: /Create monitor/ }))
    await waitFor(() => expect(apiMock.createMonitor).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Catalog API', url: 'https://catalog.example.test/health', method: 'GET', expectedStatus: 200,
    }), expect.any(Object)))
  })

  it('opens the sample workspace from the app root without an auth request', async () => {
    renderAt('/')
    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    expect(apiMock.requestAuthCode).not.toHaveBeenCalled()
    expect(apiMock.verifyAuthCode).not.toHaveBeenCalled()
  })

  it('toggles and persists the selected color theme', async () => {
    renderAt('/app')
    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'))
    expect(localStorage.getItem('api-sentinel-theme')).toBe('dark')
    expect(screen.getByRole('button', { name: 'Switch to light mode' })).toBeInTheDocument()
  })

  it('creates an unpublished status page with selected services', async () => {
    renderAt('/app/status-pages')
    fireEvent.click(await screen.findByRole('button', { name: 'Create status page' }))
    expect(await screen.findByText('Payments API')).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('Acme service status'), { target: { value: 'Acme Status' } })
    fireEvent.change(screen.getByPlaceholderText('acme-status'), { target: { value: 'acme-status' } })
    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(screen.getByRole('button', { name: 'Create unpublished page' }))
    await waitFor(() => expect(apiMock.createStatusPage).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Acme Status', slug: 'acme-status', monitorIds: ['monitor-1'],
    }), expect.any(Object)))
  })

  it('renders only public service status fields', async () => {
    renderAt('/status/acme-status')
    expect(await screen.findByRole('heading', { name: 'Acme Status' })).toBeInTheDocument()
    expect(screen.getByText('All services operational')).toBeInTheDocument()
    expect(screen.queryByText('api.private.example.test')).not.toBeInTheDocument()
  })

  it('saves the account name and time zone through the API', async () => {
    renderAt('/app/settings')
    fireEvent.change(await screen.findByLabelText('Display name'), { target: { value: 'Avery M.' } })
    fireEvent.change(screen.getByLabelText(/Time zone/), { target: { value: 'America/Los_Angeles' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save profile' }))
    await waitFor(() => expect(apiMock.updateProfile).toHaveBeenCalledWith({
      name: 'Avery M.', timeZone: 'America/Los_Angeles',
    }, expect.any(Object)))
    expect(await screen.findByText('Profile settings saved.')).toBeInTheDocument()
  })

  it('persists email notification preferences and explains unavailable SMTP', async () => {
    renderAt('/app/settings')
    const incidentEmails = await screen.findByLabelText('Incident detected emails')
    fireEvent.click(incidentEmails)
    fireEvent.click(screen.getByRole('button', { name: 'Save preferences' }))
    await waitFor(() => expect(apiMock.updateNotifications).toHaveBeenCalledWith({
      incidentEmailsEnabled: false, recoveryEmailsEnabled: true,
    }, expect.any(Object)))
    expect(screen.getByText(/Email delivery is not configured/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Send test email' })).toBeDisabled()
  })

  it('acknowledges an open incident through the server action', async () => {
    apiMock.listIncidents.mockResolvedValue([{
      id: 'incident-1', monitorId: 'monitor-1', monitorName: 'Payments API', summary: 'Unexpected response',
      state: 'OPEN', failureCategory: 'HTTP_STATUS', startedAt: '2026-01-01T00:00:00Z',
    }])
    renderAt('/app/incidents')
    fireEvent.click(await screen.findByRole('button', { name: 'Acknowledge incident for Payments API' }))
    await waitFor(() => expect(apiMock.acknowledgeIncident).toHaveBeenCalledWith('incident-1', expect.any(Object)))
  })

  it('opens monitor details with persisted configuration and check history', async () => {
    renderAt('/app/monitors/monitor-1')
    expect(await screen.findByRole('heading', { name: 'Payments API' })).toBeInTheDocument()
    expect(screen.getByText(/GET · https:\/\/api\.example\.test\/health/)).toBeInTheDocument()
    expect(await screen.findByText('Expected response received.')).toBeInTheDocument()
    expect(apiMock.getMonitorChecks).toHaveBeenCalledWith('monitor-1')
  })

  it('edits monitor settings using the persisted monitor configuration', async () => {
    renderAt('/app/monitors/monitor-1')
    fireEvent.click(await screen.findByRole('button', { name: 'Edit settings' }))
    expect(await screen.findByRole('heading', { name: 'Edit monitor' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Monitor name'), { target: { value: 'Payments edge API' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(apiMock.updateMonitor).toHaveBeenCalledWith('monitor-1', expect.objectContaining({
      name: 'Payments edge API', url: sampleMonitor.url, expectedStatus: 200,
      failureThreshold: 2, recoveryThreshold: 2, responseTimeThresholdMs: 1200,
    })))
  })
})