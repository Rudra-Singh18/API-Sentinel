import { createContext, useContext, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, AlertTriangle, ArrowRight, Bell, Check, ChevronDown, Clock3, Cloud, Gauge, LayoutDashboard, Menu, Moon, MoreHorizontal, Plus, Radio, Search, Settings, ShieldCheck, Siren, Sun, X } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, DEMO_MODE } from './lib/api'
import SettingsPage from './SettingsPage'
import type { Incident, Monitor, MonitorInput } from './types'
import './App.css'
import './status-pages.css'
import './monitor-detail.css'
import './dark-mode.css'

type Theme = 'light' | 'dark'
const ThemeContext = createContext<{ theme: Theme; toggleTheme: () => void }>({ theme: 'light', toggleTheme: () => undefined })

const links = [
  { label: 'Overview', to: '/app', icon: LayoutDashboard },
  { label: 'Monitors', to: '/app/monitors', icon: Radio },
  { label: 'Incidents', to: '/app/incidents', icon: Siren },
  { label: 'Status pages', to: '/app/status-pages', icon: Cloud },
  { label: 'Settings', to: '/app/settings', icon: Settings },
]
const EMPTY_MONITORS: Monitor[] = []
const EMPTY_INCIDENTS: Incident[] = []

export default function App() {
  const [theme, setTheme] = useState<Theme>(() => localStorage.getItem('api-sentinel-theme') === 'dark' ? 'dark' : 'light')
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('api-sentinel-theme', theme)
  }, [theme])
  const toggleTheme = () => setTheme((current) => current === 'dark' ? 'light' : 'dark')
  return <ThemeContext.Provider value={{ theme, toggleTheme }}><Routes>
    <Route path="/" element={<Navigate to="/app" replace />} />
    <Route path="/welcome" element={<Landing />} />
    <Route path="/login" element={<AuthPage key="login" mode="login" />} />
    <Route path="/register" element={<AuthPage key="register" mode="register" />} />
    <Route path="/app/*" element={<ProtectedConsole />} />
    <Route path="/status/:slug" element={<PublicStatus />} />
    <Route path="*" element={<Navigate to="/app" replace />} />
  </Routes></ThemeContext.Provider>
}

function ThemeToggle() {
  const { theme, toggleTheme } = useContext(ThemeContext)
  const Icon = theme === 'dark' ? Sun : Moon
  const nextTheme = theme === 'dark' ? 'light' : 'dark'
  return <button className="theme-toggle" type="button" aria-label={`Switch to ${nextTheme} mode`} title={`Switch to ${nextTheme} mode`} onClick={toggleTheme}><Icon size={16} /></button>
}

function ProtectedConsole() {
  const session = useQuery({ queryKey: ['me'], queryFn: api.me, enabled: !DEMO_MODE, retry: false })
  if (DEMO_MODE) return <Console />
  if (session.isPending) return <main className="auth-page"><div className="auth-message">Checking your session…</div></main>
  if (session.isError) return <Navigate to="/login" replace />
  return <Console />
}

function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const navigate = useNavigate()
  const client = useQueryClient()
  const [error, setError] = useState('')
  const [email, setEmail] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const intent = mode === 'login' ? 'SIGN_IN' : 'SIGN_UP'
  const verifyMutation = useMutation({
    mutationFn: (code: string) => api.verifyAuthCode(email, code, intent),
    onSuccess: (user) => { client.setQueryData(['me'], user); void client.invalidateQueries({ queryKey: ['monitors'] }); navigate('/app', { replace: true }) },
    onError: (reason: Error) => setError(reason.message),
  })
  const requestMutation = useMutation({
    mutationFn: (address: string) => api.requestAuthCode(address, intent),
    onSuccess: () => { setCodeSent(true); setError('') },
    onError: (reason: Error) => setError(reason.message),
  })
  const submitEmail = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    requestMutation.mutate(email.trim())
  }
  const submitCode = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    const form = new FormData(event.currentTarget)
    verifyMutation.mutate(String(form.get('code') ?? '').trim())
  }
  if (DEMO_MODE) return <Navigate to="/app" replace />
  return <main className="auth-page"><div className="auth-top"><Link to="/" className="brand light-brand"><BrandMark />sentinel</Link><div className="auth-top-actions"><ThemeToggle /><Link to="/" className="auth-back">Back to site</Link></div></div><section className="auth-panel"><div className="page-kicker">API SENTINEL / WORKSPACE</div><h1>{codeSent ? 'Check your email' : mode === 'login' ? 'Welcome back' : 'Create your account'}</h1><p>{codeSent ? <>We sent a six-digit verification code to <b>{email}</b>.</> : mode === 'login' ? 'Sign in securely with a one-time email code.' : 'Verify your email to create a workspace account.'}</p>{DEMO_MODE ? <div className="demo-auth"><Activity size={18} /><b>Demo mode is enabled</b><span>Use the sample workspace to explore the interface. It does not perform live checks.</span><Link to="/app" className="button button-primary">Open demo workspace <ArrowRight size={14} /></Link></div> : codeSent ? <form key="verify-code" onSubmit={submitCode}><label className="auth-field">Verification code<input name="code" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} minLength={6} autoFocus required /></label>{error && <div className="error-notice" role="alert"><AlertTriangle size={15} /><span>{error}</span></div>}<button className="button button-primary auth-submit" disabled={verifyMutation.isPending}>{verifyMutation.isPending ? 'Verifying…' : 'Verify and continue'}</button><button className="auth-text-button" type="button" disabled={requestMutation.isPending} onClick={() => { setError(''); requestMutation.mutate(email) }}>{requestMutation.isPending ? 'Sending code…' : 'Resend code'}</button><button className="auth-text-button" type="button" onClick={() => { setCodeSent(false); setError('') }}>Use a different email</button></form> : <form key="request-code" onSubmit={submitEmail}><label className="auth-field">Email<input name="email" type="email" autoComplete="email" maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} required /></label>{error && <div className="error-notice" role="alert"><AlertTriangle size={15} /><span>{error}</span></div>}<button className="button button-primary auth-submit" disabled={requestMutation.isPending}>{requestMutation.isPending ? 'Sending code…' : mode === 'login' ? 'Email me a sign-in code' : 'Send verification code'}</button></form>}<div className="auth-switch">{mode === 'login' ? <>New to API Sentinel? <Link to="/register">Create an account</Link></> : <>Already have an account? <Link to="/login">Sign in</Link></>}</div></section><p className="auth-footnote">Verification codes expire after 10 minutes. Sessions use an HTTP-only cookie and CSRF validation.</p></main>
}

function Landing() {
  return <main className="landing">
    <nav className="landing-nav"><Link to="/" className="brand light-brand"><BrandMark />sentinel</Link><div><a href="#workflow">How it works</a><Link to="/app">Dashboard</Link><Link className="nav-cta" to="/app">Open workspace <ArrowRight size={15} /></Link><ThemeToggle /></div></nav>
    <section className="landing-hero"><div className="hero-copy"><div className="eyebrow"><i className="live-dot" /> API OBSERVABILITY, WITHOUT THE NOISE</div><h1>Know when your APIs<br />stop <em>behaving.</em></h1><p>Availability checks, response-time history, and incidents in one clear place. Catch a failing endpoint before your users do.</p><Link to="/app" className="button button-primary">Explore the workspace <ArrowRight size={16} /></Link><span className="hero-note"><ShieldCheck size={15} /> Checks run on your server, not in your browser.</span></div><div className="hero-preview"><div className="preview-bar"><span><BrandMark small /> sentinel</span><b>DEMO DATA</b></div><div className="preview-inner"><div className="preview-title"><span><small>WORKSPACE / OVERVIEW</small><strong>Good morning, Alex</strong></span><span className="status-chip up"><i /> All systems operational</span></div><div className="preview-metrics"><div><small>UPTIME · 30 DAYS</small><b>99.96<span>%</span></b><em>↗ 0.04%</em></div><div><small>AVG. RESPONSE</small><b>184<span> ms</span></b><em>↘ 12 ms</em></div><div><small>ACTIVE MONITORS</small><b>08</b><em>Across 3 projects</em></div></div><div className="preview-chart-label"><b>Response time</b><span>Last 24 hours</span></div><Chart preview /><div className="preview-service"><i className="state-dot green" /><b>Payments API</b><code>api.acme.dev/health</code><span>142 ms</span><span>Operational</span></div><div className="preview-service"><i className="state-dot amber" /><b>Search service</b><code>search.acme.dev/ping</code><span>812 ms</span><span className="warning-text">Degraded</span></div></div></div></section>
    <section className="landing-proof"><b>BUILT FOR THE PEOPLE ON CALL</b><span>Know what changed. Know when it changed. Get back to building.</span></section>
    <section className="workflow" id="workflow"><div className="eyebrow">A SMALLER DISTANCE TO THE ANSWER</div><h2>From endpoint to insight<br />in three steps.</h2><div className="workflow-grid"><article><small>01</small><Radio /><h3>Add an endpoint</h3><p>Choose a URL, expected status, and check cadence. Monitor settings remain editable.</p></article><article><small>02</small><Gauge /><h3>Let checks run</h3><p>A bounded backend worker checks endpoints and records measured response times.</p></article><article><small>03</small><Siren /><h3>Investigate clearly</h3><p>Failure thresholds open incidents; recovery checks close them with a timestamped history.</p></article></div></section>
    <footer className="landing-footer"><Link to="/" className="brand light-brand"><BrandMark />sentinel</Link><span>API Sentinel · Developer-focused endpoint monitoring</span><Link to="/app">Open workspace <ArrowRight size={14} /></Link></footer>
  </main>
}

function Console() {
  const location = useLocation()
  const client = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [mobileNav, setMobileNav] = useState(false)
  const [filter, setFilter] = useState('All monitors')
  const [search, setSearch] = useState('')
  const [toast, setToast] = useState('')
  const [menuFor, setMenuFor] = useState<string | null>(null)
  const monitorDetailId = location.pathname.match(/^\/app\/monitors\/([^/]+)$/)?.[1]
  const monitorsQuery = useQuery({ queryKey: ['monitors'], queryFn: api.listMonitors, refetchInterval: DEMO_MODE ? false : 30000 })
  const incidentsQuery = useQuery({ queryKey: ['incidents'], queryFn: api.listIncidents })
  const profileQuery = useQuery({ queryKey: ['me'], queryFn: api.me, enabled: !DEMO_MODE, retry: false })
  const monitors = monitorsQuery.data ?? EMPTY_MONITORS
  const incidents = incidentsQuery.data ?? EMPTY_INCIDENTS
  const createMutation = useMutation({ mutationFn: api.createMonitor, onSuccess: () => { void client.invalidateQueries({ queryKey: ['monitors'] }); setShowCreate(false); setToast('Monitor created. Its first check will appear when the worker runs.') } })
  const toggleMutation = useMutation({ mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => api.setMonitorEnabled(id, enabled), onSuccess: () => { void client.invalidateQueries({ queryKey: ['monitors'] }); setMenuFor(null) } })
  const deleteMutation = useMutation({ mutationFn: api.deleteMonitor, onSuccess: () => { void client.invalidateQueries({ queryKey: ['monitors'] }); setMenuFor(null); setToast('Monitor archived.') } })
  const logoutMutation = useMutation({ mutationFn: api.logout, onSuccess: () => { client.clear(); window.location.assign('/login') } })
  const displayName = profileQuery.data?.name ?? 'Demo User'
  const initials = displayName.split(/\s+/).map((part) => part[0]).slice(0, 2).join('').toUpperCase()
  const filtered = useMemo(() => monitors.filter((monitor) => (filter === 'All monitors' || monitor.status === filter) && `${monitor.name} ${monitor.url}`.toLowerCase().includes(search.toLowerCase())), [monitors, filter, search])
  const title = links.find((item) => item.to === location.pathname)?.label ?? (monitorDetailId ? 'Monitor details' : 'Overview')
  const content = monitorDetailId
    ? <MonitorDetailPage monitorId={monitorDetailId} />
    : location.pathname.endsWith('/incidents')
    ? <IncidentPage incidents={incidents} loading={incidentsQuery.isLoading} />
    : location.pathname.endsWith('/monitors')
      ? <MonitorPage monitors={filtered} loading={monitorsQuery.isLoading} error={monitorsQuery.error} filter={filter} setFilter={setFilter} search={search} setSearch={setSearch} menuFor={menuFor} setMenuFor={setMenuFor} toggle={(monitor) => toggleMutation.mutate({ id: monitor.id, enabled: !monitor.enabled })} archive={(monitor) => { if (window.confirm(`Archive ${monitor.name}? Historical checks will be retained.`)) deleteMutation.mutate(monitor.id) }} onAdd={() => setShowCreate(true)} />
      : location.pathname.endsWith('/status-pages')
        ? <StatusPagesPage monitors={monitors} />
        : location.pathname.endsWith('/settings')
        ? DEMO_MODE
          ? <DemoSettingsPage />
          : <SettingsPage />
        : <Overview monitors={monitors} incidents={incidents} loading={monitorsQuery.isLoading} error={monitorsQuery.error} add={() => setShowCreate(true)} />

  return <div className="console-shell"><aside className={`sidebar ${mobileNav ? 'mobile-open' : ''}`}><Link to="/app" className="brand"><BrandMark />sentinel<span className="edition">WORKSPACE</span></Link><div className="workspace-switch"><span>{DEMO_MODE ? 'D' : initials[0] ?? 'U'}</span><div><b>{DEMO_MODE ? 'Demo workspace' : 'Personal workspace'}</b><small>{DEMO_MODE ? 'Sample data' : 'Free plan'}</small></div><ChevronDown size={14} /></div><div className="side-label">MONITORING</div><nav>{links.map(({ label, to, icon: Icon }) => <NavLink key={to} to={to} end={to === '/app'} onClick={() => setMobileNav(false)} className={({ isActive }) => `side-link ${isActive ? 'selected' : ''}`}><Icon size={16} />{label}{label === 'Incidents' && incidents.some((incident) => incident.state !== 'RESOLVED') && <i>{incidents.filter((incident) => incident.state !== 'RESOLVED').length}</i>}</NavLink>)}</nav><div className="sidebar-bottom"><div className="usage"><span>Monitor usage</span><b>{monitors.length} / 10</b><div><i style={{ width: `${Math.min(monitors.length * 10, 100)}%` }} /></div><small>Free workspace limit</small></div><button className="profile-button" onClick={() => DEMO_MODE ? setToast('Demo mode does not use an account session.') : logoutMutation.mutate()}><span>{DEMO_MODE ? 'D' : initials}</span><div><b>{DEMO_MODE ? 'Demo workspace' : displayName}</b><small>{DEMO_MODE ? 'Sample data' : 'Sign out'}</small></div><MoreHorizontal size={17} /></button></div></aside>{mobileNav && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileNav(false)} />}
    <main className="console-main"><header className="topbar"><button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setMobileNav(true)}><Menu size={18} /></button><div className="breadcrumbs">Workspace <span>/</span><b>{title}</b></div><div className="top-actions"><span className="environment"><i className="live-dot" />{DEMO_MODE ? 'DEMO ENVIRONMENT' : 'MONITORING'}</span><ThemeToggle /><button className="icon-button" aria-label="Notifications" onClick={() => setToast('No new notifications.')}><Bell size={17} /></button>{DEMO_MODE ? <span className="top-avatar" aria-label="Demo workspace">D</span> : <button className="top-avatar" aria-label="Sign out" onClick={() => logoutMutation.mutate()}>{initials}</button>}</div></header>
      {DEMO_MODE && <div className="demo-banner"><b><Activity size={14} /> Demo workspace</b><span>Sample data only. No endpoint checks are being performed.</span><Link to="/app/monitors">Manage sample monitors <ArrowRight size={13} /></Link></div>}
      {toast && <div className="toast" role="status"><Check size={15} />{toast}<button aria-label="Dismiss" onClick={() => setToast('')}><X size={14} /></button></div>}
      <div className="page-wrap">{content}</div>
      {showCreate && <CreateMonitor close={() => { setShowCreate(false); createMutation.reset() }} submit={(input) => createMutation.mutate(input)} busy={createMutation.isPending} error={createMutation.error} />}
    </main></div>
}

function Overview({ monitors, incidents, loading, error, add }: { monitors: Monitor[]; incidents: Incident[]; loading: boolean; error: Error | null; add: () => void }) {
  const up = monitors.filter((monitor) => monitor.status === 'UP').length
  const down = monitors.filter((monitor) => monitor.status === 'DOWN').length
  const degraded = monitors.filter((monitor) => monitor.status === 'DEGRADED').length
  const response = monitors.flatMap((monitor) => monitor.latestResponseMs === null ? [] : [monitor.latestResponseMs])
  const avgResponse = response.length ? Math.round(response.reduce((sum, value) => sum + value, 0) / response.length) : null
  const uptime = monitors.flatMap((monitor) => monitor.uptimePercent === null ? [] : [monitor.uptimePercent])
  const avgUptime = uptime.length ? uptime.reduce((sum, value) => sum + value, 0) / uptime.length : null
  return <><div className="page-heading"><div><div className="page-kicker">MONITORING / WORKSPACE</div><h1>Overview</h1><p>A current read on the endpoints your team depends on.</p></div><button className="button button-primary" onClick={add}><Plus size={15} /> Add monitor</button></div>{error && <ErrorNotice message={error.message} retry={() => window.location.reload()} />}<div className="range-line"><span><Clock3 size={13} /> Last 24 hours</span><small>{loading ? 'Updating…' : 'Metrics reflect completed checks'}</small></div><section className="metric-grid"><Metric title="Active monitors" value={loading ? '—' : String(monitors.filter((item) => item.enabled).length).padStart(2, '0')} detail={`${monitors.length} configured`} icon={<Radio />} tone="blue" /><Metric title="Operational" value={loading ? '—' : String(up).padStart(2, '0')} detail={monitors.length ? `${Math.round(up / monitors.length * 100)}% of configured services` : 'No checks yet'} icon={<ShieldCheck />} tone="green" /><Metric title="Response time" value={avgResponse === null ? '—' : String(avgResponse)} suffix={avgResponse === null ? '' : 'ms'} detail={avgResponse === null ? 'Waiting for completed checks' : 'Average latest response'} icon={<Gauge />} tone="slate" /><Metric title="Open incidents" value={loading ? '—' : String(incidents.filter((item) => item.state !== 'RESOLVED').length).padStart(2, '0')} detail={down ? `${down} down` : degraded ? `${degraded} degraded` : 'No active disruptions'} icon={<Siren />} tone={down ? 'red' : 'amber'} /></section><section className="chart-grid"><div className="panel chart-panel"><div className="panel-heading"><div><h2>Response time</h2><p>Observed latency · milliseconds</p></div><span className="range-chip">24 hours <ChevronDown size={13} /></span></div><Chart /></div><div className="panel uptime-panel"><div className="panel-heading"><div><h2>Availability</h2><p>Successful expected-status checks</p></div></div><div className="uptime-value">{avgUptime === null ? '—' : avgUptime.toFixed(2)}<span>{avgUptime === null ? '' : '%'}</span></div><p className="uptime-caption">{avgUptime === null ? 'No data available' : 'Across monitors · last 24 hours'}</p><div className="uptime-bars" aria-label="Availability per monitor">{monitors.slice(0, 12).map((monitor) => <i key={monitor.id} className={monitor.status.toLowerCase()} title={`${monitor.name}: ${monitor.uptimePercent === null ? 'No data' : `${monitor.uptimePercent.toFixed(2)}%`}`} />)}</div>{!monitors.length && <p className="no-uptime">Add a monitor to collect availability history.</p>}</div></section><section className="panel data-panel"><div className="panel-heading"><div><h2>Monitors</h2><p>Your endpoints at a glance</p></div><Link className="text-link" to="/app/monitors">View all <ArrowRight size={13} /></Link></div>{loading ? <LoadingRows /> : !monitors.length ? <EmptyMonitors add={add} /> : <MonitorTable monitors={monitors.slice(0, 5)} compact />}</section><section className="panel data-panel"><div className="panel-heading"><div><h2>Recent incidents</h2><p>Disruptions and recoveries across your services</p></div><Link className="text-link" to="/app/incidents">All incidents <ArrowRight size={13} /></Link></div>{incidents.length ? <IncidentRows incidents={incidents.slice(0, 3)} /> : <div className="no-incidents"><span><Check size={16} /></span><div><b>No incidents recorded</b><p>Incidents appear after a monitor reaches its failure threshold.</p></div></div>}</section></>
}

function Metric({ title, value, detail, icon, tone, suffix = '' }: { title: string; value: string; detail: string; icon: ReactNode; tone: string; suffix?: string }) { return <article className="metric"><div className="metric-head"><span>{title}</span><i className={tone}>{icon}</i></div><div className="metric-value">{value}<small>{suffix}</small></div><div className="metric-detail">{detail}</div></article> }

function MonitorPage({ monitors, loading, error, filter, setFilter, search, setSearch, menuFor, setMenuFor, toggle, archive, onAdd }: { monitors: Monitor[]; loading: boolean; error: Error | null; filter: string; setFilter: (value: string) => void; search: string; setSearch: (value: string) => void; menuFor: string | null; setMenuFor: (value: string | null) => void; toggle: (monitor: Monitor) => void; archive: (monitor: Monitor) => void; onAdd: () => void }) {
  return <><div className="page-heading"><div><div className="page-kicker">ENDPOINTS / INVENTORY</div><h1>Monitors</h1><p>Configure checks and inspect endpoint health.</p></div><button className="button button-primary" onClick={onAdd}><Plus size={15} /> Add monitor</button></div><div className="toolbar"><label className="search-box"><Search size={15} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search monitors or endpoints" aria-label="Search monitors" /></label><select value={filter} onChange={(event) => setFilter(event.target.value)} aria-label="Filter by status">{['All monitors', 'UP', 'DEGRADED', 'DOWN', 'PENDING', 'PAUSED'].map((value) => <option key={value}>{value}</option>)}</select><span>{monitors.length} monitor{monitors.length === 1 ? '' : 's'}</span></div>{error && <ErrorNotice message={error.message} retry={() => window.location.reload()} />}{loading ? <LoadingRows /> : monitors.length ? <MonitorTable monitors={monitors} menuFor={menuFor} setMenuFor={setMenuFor} toggle={toggle} archive={archive} /> : <div className="panel empty-table"><Radio size={22} /><b>No monitors match</b><span>Create a monitor or adjust your filters.</span><button className="button button-primary" onClick={onAdd}><Plus size={14} /> Add a monitor</button></div>}</>
}

function MonitorDetailPage({ monitorId }: { monitorId: string }) {
  const client = useQueryClient()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const monitor = useQuery({ queryKey: ['monitor', monitorId], queryFn: () => api.getMonitor(monitorId) })
  const checks = useQuery({ queryKey: ['monitor-checks', monitorId], queryFn: () => api.getMonitorChecks(monitorId), refetchInterval: 30000 })
  const incidents = useQuery({ queryKey: ['incidents'], queryFn: api.listIncidents })
  const changeEnabled = useMutation({
    mutationFn: (enabled: boolean) => api.setMonitorEnabled(monitorId, enabled),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['monitor', monitorId] })
      void client.invalidateQueries({ queryKey: ['monitors'] })
    },
  })
  const archive = useMutation({
    mutationFn: () => api.deleteMonitor(monitorId),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['monitors'] })
      navigate('/app/monitors')
    },
  })
  const update = useMutation({
    mutationFn: (input: MonitorInput) => api.updateMonitor(monitorId, input),
    onSuccess: () => {
      setEditing(false)
      void client.invalidateQueries({ queryKey: ['monitor', monitorId] })
      void client.invalidateQueries({ queryKey: ['monitors'] })
      void client.invalidateQueries({ queryKey: ['monitor-checks', monitorId] })
    },
  })
  if (monitor.isPending) return <LoadingRows />
  if (monitor.isError) return <><ErrorNotice message={monitor.error.message} retry={() => void monitor.refetch()} /><Link className="text-link" to="/app/monitors">Back to monitors</Link></>

  const item = monitor.data
  const successfulChecks = checks.data?.filter((check) => check.outcome === 'AVAILABLE' && check.responseTimeMs !== null) ?? []
  const average = successfulChecks.length ? Math.round(successfulChecks.reduce((total, check) => total + (check.responseTimeMs ?? 0), 0) / successfulChecks.length) : null
  const lastSuccess = successfulChecks[0]
  const relatedIncidents = incidents.data?.filter((incident) => incident.monitorId === monitorId) ?? []
  return <><div className="detail-back"><Link className="text-link" to="/app/monitors">← All monitors</Link></div><div className="page-heading monitor-detail-heading"><div><div className="page-kicker">ENDPOINT / DETAILS</div><h1>{item.name}</h1><p className="detail-endpoint">{item.method} · {item.url}</p><Status status={item.status} /></div><div className="detail-actions"><button className="button quiet-button" onClick={() => setEditing(true)}>Edit settings</button><button className="button quiet-button" disabled={changeEnabled.isPending} onClick={() => changeEnabled.mutate(!item.enabled)}>{item.enabled ? 'Pause monitor' : 'Resume monitor'}</button><button className="button quiet-button danger-button" disabled={archive.isPending} onClick={() => { if (window.confirm(`Archive ${item.name}? Historical checks will be retained.`)) archive.mutate() }}>{archive.isPending ? 'Archiving…' : 'Archive'}</button></div></div>
    {changeEnabled.error && <ErrorNotice message={changeEnabled.error.message} retry={() => changeEnabled.reset()} />}
    {archive.error && <ErrorNotice message={archive.error.message} retry={() => archive.reset()} />}
    {update.error && <ErrorNotice message={update.error.message} retry={() => update.reset()} />}
    <section className="detail-summary">
      <article><span>{DEMO_MODE ? 'Sample response' : 'Latest response'}</span><b>{item.latestResponseMs === null ? '—' : `${item.latestResponseMs} ms`}</b></article>
      <article><span>{DEMO_MODE ? 'Sample response average' : 'Average · recent 50 checks'}</span><b>{DEMO_MODE ? 'Sample only' : average === null ? 'No data' : `${average} ms`}</b></article>
      <article><span>{DEMO_MODE ? 'Sample timestamp' : 'Last check'}</span><b>{item.lastCheckedAt ? new Date(item.lastCheckedAt).toLocaleString() : 'First check pending'}</b></article>
      <article><span>{DEMO_MODE ? 'Sample history' : 'Last successful check'}</span><b>{DEMO_MODE ? 'Not a live check' : lastSuccess ? new Date(lastSuccess.completedAt).toLocaleString() : 'No successful check yet'}</b></article>
    </section>
    <section className="detail-columns"><div className="panel detail-panel"><div className="panel-heading"><div><h2>Configuration</h2><p>{DEMO_MODE ? 'Sample monitor configuration' : 'Current server-side monitor settings'}</p></div></div><dl className="detail-config"><div><dt>Expected status</dt><dd>{item.expectedStatus}</dd></div><div><dt>Check interval</dt><dd>{item.intervalSeconds} seconds</dd></div><div><dt>Request timeout</dt><dd>{item.timeoutMs} ms</dd></div><div><dt>{DEMO_MODE ? 'Sample availability · 24h' : 'Availability · last 24 hours'}</dt><dd>{item.uptimePercent === null ? 'No data available' : `${item.uptimePercent.toFixed(2)}%`}</dd></div></dl></div><div className="panel detail-panel"><div className="panel-heading"><div><h2>Related incidents</h2><p>Incidents associated with this monitor</p></div><Link className="text-link" to="/app/incidents">View all</Link></div>{relatedIncidents.length ? <IncidentRows incidents={relatedIncidents} /> : <p className="detail-empty">No incidents recorded for this monitor.</p>}</div></section>
    <section className="panel data-panel"><div className="panel-heading"><div><h2>Check history</h2><p>{DEMO_MODE ? 'Illustrative sample values are not real checks.' : 'Most recent 50 persisted checks'}</p></div></div>{DEMO_MODE ? <p className="detail-empty">Demo mode does not perform or store endpoint checks.</p> : checks.isError ? <ErrorNotice message={checks.error.message} retry={() => void checks.refetch()} /> : checks.isPending ? <LoadingRows /> : !checks.data.length ? <p className="detail-empty">{item.status === 'PENDING' ? 'The first health check is pending.' : 'No check history is available.'}</p> : <div className="table-scroll"><table><thead><tr><th>COMPLETED</th><th>RESULT</th><th>HTTP</th><th>RESPONSE</th><th>DETAIL</th></tr></thead><tbody>{checks.data.map((check) => <tr key={check.id}><td className="muted-cell">{new Date(check.completedAt).toLocaleString()}</td><td><Status status={check.outcome === 'AVAILABLE' ? 'UP' : 'DOWN'} /></td><td className="mono">{check.httpStatus ?? '—'}</td><td className="mono">{check.responseTimeMs === null ? '—' : `${check.responseTimeMs} ms`}</td><td className="muted-cell">{check.diagnosticMessage ?? 'Expected response received.'}</td></tr>)}</tbody></table></div>}</section>
    {editing && <CreateMonitor initial={item} close={() => { setEditing(false); update.reset() }} submit={(input) => update.mutate(input)} busy={update.isPending} error={update.error} />}
  </>
}

function MonitorTable({ monitors, compact = false, menuFor = null, setMenuFor = () => undefined, toggle = () => undefined, archive = () => undefined }: { monitors: Monitor[]; compact?: boolean; menuFor?: string | null; setMenuFor?: (id: string | null) => void; toggle?: (monitor: Monitor) => void; archive?: (monitor: Monitor) => void }) {
  return <div className={`table-scroll ${compact ? 'compact' : ''}`}><table><thead><tr><th>MONITOR</th><th>STATUS</th><th>RESPONSE</th><th>LAST CHECK</th><th>UPTIME · 24H</th><th /></tr></thead><tbody>{monitors.map((monitor) => <tr key={monitor.id}><td><div className="monitor-name"><i className={`state-dot ${tone(monitor.status)}`} /><span><b><Link to={`/app/monitors/${monitor.id}`} className="monitor-detail-link">{monitor.name}</Link></b><code>{host(monitor.url)}</code></span></div></td><td><Status status={monitor.status} /></td><td className="mono">{monitor.latestResponseMs === null ? '—' : `${monitor.latestResponseMs} ms`}</td><td className="muted-cell">{monitor.lastCheckedAt ? relative(monitor.lastCheckedAt) : 'Awaiting first check'}</td><td className="uptime-cell">{monitor.uptimePercent === null ? '—' : `${monitor.uptimePercent.toFixed(2)}%`}</td><td className="actions-cell">{!compact && <div className="row-menu"><button className="more-button" aria-label={`Actions for ${monitor.name}`} onClick={() => setMenuFor(menuFor === monitor.id ? null : monitor.id)}><MoreHorizontal size={17} /></button>{menuFor === monitor.id && <div className="action-menu"><button onClick={() => toggle(monitor)}>{monitor.enabled ? 'Pause monitor' : 'Resume monitor'}</button><button className="danger" onClick={() => archive(monitor)}>Archive monitor</button></div>}</div>}</td></tr>)}</tbody></table></div>
}

function IncidentPage({ incidents, loading }: { incidents: Incident[]; loading: boolean }) {
  const client = useQueryClient()
  const acknowledge = useMutation({
    mutationFn: api.acknowledgeIncident,
    onSuccess: () => { void client.invalidateQueries({ queryKey: ['incidents'] }) },
  })
  return <><div className="page-heading"><div><div className="page-kicker">MONITORING / EVENTS</div><h1>Incidents</h1><p>Failures that crossed a monitor's configured threshold.</p></div></div>{acknowledge.error && <ErrorNotice message={acknowledge.error.message} retry={() => acknowledge.reset()} />}{loading ? <LoadingRows /> : incidents.length ? <section className="panel data-panel"><IncidentRows incidents={incidents} onAcknowledge={(id) => acknowledge.mutate(id)} busy={acknowledge.isPending} /></section> : <div className="panel empty-table"><ShieldCheck size={22} /><b>No incidents</b><span>Incident history appears as checks run.</span></div>}</>
}
function IncidentRows({ incidents, onAcknowledge, busy = false }: { incidents: Incident[]; onAcknowledge?: (id: string) => void; busy?: boolean }) { return <div className="incident-list">{incidents.map((item) => <article className="incident-row" key={item.id}><span className={`incident-icon ${item.state.toLowerCase()}`}>{item.state === 'RESOLVED' ? <Check size={15} /> : <AlertTriangle size={15} />}</span><div className="incident-copy"><b>{item.summary}</b><span>{item.monitorName} · {item.failureCategory ?? 'Unexpected response'}</span></div><div className="incident-time"><b>{item.state === 'RESOLVED' ? 'Resolved' : item.state === 'ACKNOWLEDGED' ? 'Acknowledged' : 'Open'}</b><span>{relative(item.startedAt)}</span></div>{item.state === 'OPEN' && onAcknowledge && <button className="button quiet-button incident-ack" disabled={busy} onClick={() => onAcknowledge(item.id)} aria-label={`Acknowledge incident for ${item.monitorName}`}>{busy ? 'Saving…' : 'Acknowledge'}</button>}</article>)}</div> }

function CreateMonitor({ close, submit, busy, error, initial }: { close: () => void; submit: (input: MonitorInput) => void; busy: boolean; error: Error | null; initial?: Monitor }) {
  const [validation, setValidation] = useState('')
  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const name = String(form.get('name') ?? '').trim()
    const url = String(form.get('url') ?? '').trim()
    let parsed: URL
    try { parsed = new URL(url) } catch { setValidation('Enter a valid URL.'); return }
    if (!name || name.length > 80) { setValidation('Enter a monitor name up to 80 characters.'); return }
    if (!['https:', 'http:'].includes(parsed.protocol) || parsed.username || parsed.password || (!DEMO_MODE && parsed.protocol !== 'https:')) {
      setValidation(DEMO_MODE ? 'Use a valid HTTP or HTTPS URL without credentials.' : 'Use an HTTPS URL without embedded credentials.')
      return
    }
    setValidation('')
    submit({
      name, url, method: String(form.get('method')) as 'GET' | 'HEAD',
      expectedStatus: Number(form.get('expectedStatus')), intervalSeconds: Number(form.get('intervalSeconds')),
      timeoutMs: Number(form.get('timeoutMs')), failureThreshold: Number(form.get('failureThreshold')),
      recoveryThreshold: Number(form.get('recoveryThreshold')), responseTimeThresholdMs: Number(form.get('responseTimeThresholdMs')),
    })
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) close() }}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="create-title"><div className="modal-heading"><div><div className="page-kicker">{initial ? 'MONITOR SETTINGS' : 'NEW ENDPOINT'}</div><h2 id="create-title">{initial ? 'Edit monitor' : 'Add a monitor'}</h2><p>Configure the request, thresholds, and expected response.</p></div><button className="icon-button" aria-label="Close dialog" onClick={close}><X size={17} /></button></div><form onSubmit={onSubmit}><label className="field">Monitor name<input name="name" maxLength={80} placeholder="e.g. Payments API" defaultValue={initial?.name} required /></label><label className="field">Target URL<input name="url" type="url" placeholder="https://api.example.com/health" defaultValue={initial?.url} required /><small>HTTPS required. Private network destinations are blocked by the API.</small></label><div className="field-grid"><label className="field">HTTP method<select name="method" defaultValue={initial?.method ?? 'GET'}><option>GET</option><option>HEAD</option></select></label><label className="field">Expected status<input name="expectedStatus" type="number" min="100" max="599" defaultValue={initial?.expectedStatus ?? 200} required /></label><label className="field">Check interval<select name="intervalSeconds" defaultValue={initial?.intervalSeconds ?? 60}><option value="60">Every minute</option><option value="300">Every 5 minutes</option><option value="900">Every 15 minutes</option></select></label><label className="field">Timeout<select name="timeoutMs" defaultValue={initial?.timeoutMs ?? 5000}><option value="3000">3 seconds</option><option value="5000">5 seconds</option><option value="10000">10 seconds</option></select></label><label className="field">Failure threshold<input name="failureThreshold" type="number" min="1" max="10" defaultValue={initial?.failureThreshold ?? 2} required /><small>Consecutive failures before opening an incident.</small></label><label className="field">Recovery threshold<input name="recoveryThreshold" type="number" min="1" max="10" defaultValue={initial?.recoveryThreshold ?? 2} required /><small>Consecutive successes before resolving an incident.</small></label><label className="field">Slow-response threshold<input name="responseTimeThresholdMs" type="number" min="100" max="60000" defaultValue={initial?.responseTimeThresholdMs ?? 1200} required /><small>Successful checks slower than this are degraded.</small></label></div>{DEMO_MODE && <div className="demo-form-note">Demo mode stores sample records only. No HTTP check will run.</div>}{(validation || error) && <ErrorNotice message={validation || error?.message || 'Could not save monitor.'} retry={() => undefined} />}<div className="modal-actions"><button type="button" className="button quiet-button" onClick={close}>Cancel</button><button className="button button-primary" disabled={busy}>{busy ? 'Saving…' : <>{initial ? <Check size={14} /> : <Plus size={14} />}{initial ? 'Save changes' : 'Create monitor'}</>}</button></div></form></section></div>
}

function DemoSettingsPage() { return <><div className="page-heading"><div><div className="page-kicker">WORKSPACE / CONFIGURATION</div><h1>Settings</h1><p>Account settings are available in the connected monitoring workspace.</p></div></div><section className="panel placeholder"><span><Settings /></span><h2>Demo mode has no account settings</h2><p>Sample workspace data is isolated and not persisted to an account. Profile and notification preferences are unavailable in the demo workspace.</p></section></> }

function StatusPagesPage({ monitors }: { monitors: Monitor[] }) {
  const client = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [selected, setSelected] = useState<string[]>([])
  const pages = useQuery({ queryKey: ['status-pages'], queryFn: api.listStatusPages, enabled: !DEMO_MODE })
  const create = useMutation({ mutationFn: api.createStatusPage, onSuccess: () => { void client.invalidateQueries({ queryKey: ['status-pages'] }); setShowForm(false); setSelected([]) } })
  const publish = useMutation({ mutationFn: ({ id, published }: { id: string; published: boolean }) => api.setStatusPagePublished(id, published), onSuccess: () => { void client.invalidateQueries({ queryKey: ['status-pages'] }) } })
  const remove = useMutation({ mutationFn: api.deleteStatusPage, onSuccess: () => { void client.invalidateQueries({ queryKey: ['status-pages'] }) } })
  const submit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const values = new FormData(event.currentTarget); create.mutate({ title: String(values.get('title')).trim(), slug: String(values.get('slug')).trim().toLowerCase(), description: String(values.get('description') ?? '').trim(), monitorIds: selected }) }
  return <><div className="page-heading"><div><div className="page-kicker">WORKSPACE / PUBLIC</div><h1>Status pages</h1><p>Publish safe, read-only service health for your users.</p></div>{!DEMO_MODE && <button className="button button-primary" onClick={() => setShowForm(!showForm)}><Plus size={14} /> {showForm ? 'Close form' : 'Create status page'}</button>}</div>{DEMO_MODE ? <section className="panel placeholder"><span><Cloud /></span><h2>Status-page management needs a connected account</h2><p>Demo mode uses sample monitor data only and cannot publish a live status page.</p></section> : <>{pages.error && <ErrorNotice message={pages.error.message} retry={() => void pages.refetch()} />}{showForm && <form className="panel status-form" onSubmit={submit}><h2>New public status page</h2><div className="field-grid"><label className="field">Page title<input name="title" maxLength={100} required placeholder="Acme service status" /></label><label className="field">Public URL slug<input name="slug" maxLength={64} required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" placeholder="acme-status" /><small>Lowercase letters, numbers, and hyphens.</small></label></div><label className="field">Description<input name="description" maxLength={500} placeholder="Current service availability" /></label><fieldset className="monitor-picker"><legend>Services to display</legend>{monitors.length ? monitors.map((monitor) => <label key={monitor.id}><input type="checkbox" checked={selected.includes(monitor.id)} onChange={(event) => setSelected(event.target.checked ? [...selected, monitor.id] : selected.filter((id) => id !== monitor.id))} /><span>{monitor.name}</span><Status status={monitor.status} /></label>) : <p>Create a monitor before selecting services.</p>}</fieldset>{create.error && <ErrorNotice message={create.error.message} retry={() => undefined} />}<div className="modal-actions"><button type="submit" className="button button-primary" disabled={create.isPending || selected.length === 0}>{create.isPending ? 'Saving…' : 'Create unpublished page'}</button></div></form>}{pages.isLoading ? <LoadingRows /> : pages.data?.length ? <section className="panel status-page-list">{pages.data.map((page) => <article className="status-page-row" key={page.id}><div><b>{page.title}</b><code>/status/{page.slug}</code><small>{page.monitors.length} service{page.monitors.length === 1 ? '' : 's'} · Updated {relative(page.updatedAt)}</small></div><span className={`status-chip ${page.published ? 'up' : 'paused'}`}><i />{page.published ? 'Published' : 'Unpublished'}</span>{page.published && <Link className="text-link" to={`/status/${page.slug}`} target="_blank" rel="noreferrer">Open public page <ArrowRight size={13} /></Link>}<button className="button quiet-button" onClick={() => publish.mutate({ id: page.id, published: !page.published })}>{page.published ? 'Unpublish' : 'Publish'}</button><button className="text-danger" onClick={() => window.confirm(`Delete ${page.title}?`) && remove.mutate(page.id)}>Delete</button></article>)}</section> : !showForm && <div className="panel empty-table"><Cloud size={22} /><b>No status pages yet</b><span>Choose services to publish a read-only status summary.</span><button className="button button-primary" onClick={() => setShowForm(true)}><Plus size={14} /> Create status page</button></div>}</>}</>
}

function PublicStatus() {
  const { slug = '' } = useParams()
  const query = useQuery({ queryKey: ['public-status', slug], queryFn: () => api.getPublicStatus(slug), enabled: !!slug })
  if (query.isLoading) return <main className="public-status"><div className="public-status-body">Loading status…</div></main>
  if (query.isError || !query.data) return <main className="public-status"><header><Link to="/" className="brand light-brand"><BrandMark />sentinel</Link><ThemeToggle /></header><section><div className="page-kicker">PUBLIC STATUS</div><h1>Status page unavailable</h1><p>This status page has not been published or does not exist.</p><Link className="text-link" to="/">Back to API Sentinel <ArrowRight size={14} /></Link></section></main>
  const page = query.data
  return <main className="public-status"><header><Link to="/" className="brand light-brand"><BrandMark />sentinel</Link><div className="public-status-actions"><span className="status-chip"><i />Updated {relative(page.generatedAt)}</span><ThemeToggle /></div></header><section className="public-content"><div className="page-kicker">SERVICE STATUS</div><h1>{page.title}</h1><p>{page.description}</p><div className={`overall-status ${page.overallStatus.toLowerCase()}`}><Status status={page.overallStatus} /><b>{page.overallStatus === 'UP' ? 'All services operational' : page.overallStatus === 'PENDING' ? 'Status data is not available yet' : 'Service disruption detected'}</b></div><div className="public-service-list">{page.services.map((service) => <article key={service.name}><span><i className={`state-dot ${tone(service.status)}`} /><b>{service.name}</b></span><Status status={service.status} /><span>{service.uptimePercent === null ? 'No uptime data' : `${service.uptimePercent.toFixed(2)}% uptime · 24h`}</span></article>)}</div>{page.incidents.length > 0 && <section className="public-incidents"><h2>Recent incidents</h2>{page.incidents.map((incident, index) => <article key={`${incident.startedAt}-${index}`}><b>{incident.monitorName} · {incident.state === 'RESOLVED' ? 'Resolved' : 'Investigating'}</b><span>Started {relative(incident.startedAt)}{incident.resolvedAt ? ` · Resolved ${relative(incident.resolvedAt)}` : ''}</span></article>)}</section>}<p className="public-generated">Status sampled from completed HTTP checks · {relative(page.generatedAt)}</p></section></main>
}

function Chart({ preview = false }: { preview?: boolean }) {
  const query = useQuery({ queryKey: ['performance'], queryFn: api.getPerformance, enabled: !preview, refetchInterval: DEMO_MODE ? false : 60000 })
  const data = preview ? demoPoints : query.data ?? []
  return <div className={`chart-area ${preview ? 'preview-chart' : ''}`}>{data.length ? <ResponsiveContainer width="100%" height="100%"><AreaChart data={data} margin={{ top: 8, right: 5, bottom: 0, left: -20 }}><defs><linearGradient id={preview ? 'preview-fill' : 'main-fill'} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#2879c3" stopOpacity={0.18} /><stop offset="95%" stopColor="#2879c3" stopOpacity={0.01} /></linearGradient></defs><CartesianGrid vertical={false} stroke="#e9edf1" strokeDasharray="3 4" /><XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: '#87919b', fontSize: 9 }} interval={preview ? 5 : 'preserveStartEnd'} /><YAxis tickLine={false} axisLine={false} tick={{ fill: '#87919b', fontSize: 9 }} width={34} /><Tooltip contentStyle={{ border: '1px solid #e1e6eb', borderRadius: 4, fontSize: 11 }} formatter={(value) => [`${value} ms`, 'Response']} /><Area type="monotone" dataKey="responseMs" stroke="#2879c3" strokeWidth={2} fill={`url(#${preview ? 'preview-fill' : 'main-fill'})`} connectNulls={false} /></AreaChart></ResponsiveContainer> : <div className="chart-empty"><Activity size={17} />{query.isLoading ? 'Loading response history…' : 'No response-time history yet'}</div>}</div>
}
function Status({ status }: { status: Monitor['status'] }) { return <span className={`status-chip ${status.toLowerCase()}`}><i />{status[0] + status.slice(1).toLowerCase()}</span> }
function BrandMark({ small = false }: { small?: boolean }) { return <i className={`brand-mark ${small ? 'small' : ''}`}><Activity size={small ? 12 : 16} /></i> }
function ErrorNotice({ message, retry }: { message: string; retry: () => void }) { return <div className="error-notice"><AlertTriangle size={15} /><span>{message}</span><button onClick={retry}>Retry</button></div> }
function EmptyMonitors({ add }: { add: () => void }) { return <div className="empty-state"><span><Radio size={22} /></span><div><h3>No monitors configured</h3><p>Add an endpoint to start collecting availability and response-time history.</p></div><button className="button button-primary" onClick={add}><Plus size={14} /> Add your first monitor</button></div> }
function LoadingRows() { return <div className="loading-rows" aria-label="Loading"><i /><i /><i /><i /></div> }
function tone(status: Monitor['status']) { return status === 'UP' ? 'green' : status === 'DEGRADED' ? 'amber' : status === 'DOWN' ? 'red' : 'slate' }
function host(value: string) { try { const parsed = new URL(value); return `${parsed.host}${parsed.pathname}` } catch { return value } }
function relative(value: string) { const mins = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 60000)); return !Number.isFinite(mins) ? '—' : mins < 1 ? 'Just now' : mins < 60 ? `${mins}m ago` : mins < 1440 ? `${Math.floor(mins / 60)}h ago` : `${Math.floor(mins / 1440)}d ago` }
const demoPoints = Array.from({ length: 25 }, (_, i) => ({ label: `${String(i).padStart(2, '0')}:00`, responseMs: Math.round(165 + Math.sin(i * .78) * 33 + Math.cos(i * .31) * 22 + (i === 17 ? 115 : 0)) }))
