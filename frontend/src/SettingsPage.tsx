import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, Mail, UserRound } from 'lucide-react'
import { api } from './lib/api'
import './settings-page.css'

const TIME_ZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Asia/Kolkata',
  'Asia/Tokyo',
  'Australia/Sydney',
]

export default function SettingsPage() {
  const client = useQueryClient()
  const [profileMessage, setProfileMessage] = useState('')
  const [notificationMessage, setNotificationMessage] = useState('')
  const settings = useQuery({ queryKey: ['account-settings'], queryFn: api.getAccountSettings })
  const profile = useMutation({
    mutationFn: api.updateProfile,
    onSuccess: (data) => {
      client.setQueryData(['account-settings'], data)
      client.setQueryData(['me'], { id: data.id, name: data.name, email: data.email, createdAt: data.createdAt })
      setProfileMessage('Profile settings saved.')
    },
  })
  const notifications = useMutation({
    mutationFn: api.updateNotifications,
    onSuccess: (data) => {
      client.setQueryData(['account-settings'], data)
      setNotificationMessage('Notification preferences saved.')
    },
  })
  const testEmail = useMutation({
    mutationFn: api.sendTestEmail,
    onSuccess: () => setNotificationMessage('Test email sent. Check your inbox.'),
  })

  const saveProfile = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setProfileMessage('')
    profile.mutate({ name: String(form.get('name') ?? '').trim(), timeZone: String(form.get('timeZone') ?? 'UTC') })
  }
  const saveNotifications = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setNotificationMessage('')
    notifications.mutate({
      incidentEmailsEnabled: form.get('incidentEmailsEnabled') === 'on',
      recoveryEmailsEnabled: form.get('recoveryEmailsEnabled') === 'on',
    })
  }

  if (settings.isPending) return <SettingsFrame><div className="settings-state">Loading account settings…</div></SettingsFrame>
  if (settings.isError) return <SettingsFrame><div className="settings-state settings-error" role="alert"><AlertTriangle size={17} />{settings.error.message}<button className="button quiet-button" onClick={() => void settings.refetch()}>Retry</button></div></SettingsFrame>

  const data = settings.data
  return <SettingsFrame>
    <section className="settings-panel">
      <div className="settings-panel-heading"><span><UserRound size={17} /></span><div><h2>Account</h2><p>Your profile and display preferences.</p></div></div>
      <form className="settings-form" onSubmit={saveProfile}>
        <label className="field">Display name<input name="name" defaultValue={data.name} maxLength={100} required /></label>
        <label className="field">Email address<input value={data.email} readOnly /><small>Email changes are not available in this release.</small></label>
        <label className="field">Time zone<select name="timeZone" defaultValue={data.timeZone}>{!TIME_ZONES.includes(data.timeZone) && <option value={data.timeZone}>{data.timeZone}</option>}{TIME_ZONES.map((zone) => <option key={zone} value={zone}>{zone.replaceAll('_', ' ')}</option>)}</select><small>Timestamps are stored in UTC and displayed using this preference.</small></label>
        {profileMessage && <div className="settings-feedback" role="status"><Check size={14} />{profileMessage}</div>}
        {profile.error && <SettingsError message={profile.error.message} />}
        <button className="button button-primary" disabled={profile.isPending}>{profile.isPending ? 'Saving…' : 'Save profile'}</button>
      </form>
      <div className="account-meta"><span>Account created</span><b>{new Date(data.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium', timeZone: data.timeZone })}</b></div>
    </section>

    <section className="settings-panel settings-notifications">
      <div className="settings-panel-heading"><span><Mail size={17} /></span><div><h2>Email notifications</h2><p>Choose which incident lifecycle events should send email.</p></div></div>
      <div className={`mail-config ${data.emailConfigured ? 'configured' : ''}`} role="status">
        <i />{data.emailConfigured ? 'SMTP is configured for this deployment.' : 'Email delivery is not configured. Ask your administrator to configure SMTP.'}
      </div>
      <form className="settings-form" onSubmit={saveNotifications}>
        <label className="settings-toggle"><span><b>Incident detected</b><small>Email when a monitor reaches its failure threshold.</small></span><input aria-label="Incident detected emails" type="checkbox" name="incidentEmailsEnabled" defaultChecked={data.incidentEmailsEnabled} /></label>
        <label className="settings-toggle"><span><b>Incident recovered</b><small>Email after the configured recovery checks succeed.</small></span><input aria-label="Incident recovered emails" type="checkbox" name="recoveryEmailsEnabled" defaultChecked={data.recoveryEmailsEnabled} /></label>
        {notificationMessage && <div className="settings-feedback" role="status"><Check size={14} />{notificationMessage}</div>}
        {notifications.error && <SettingsError message={notifications.error.message} />}
        {testEmail.error && <SettingsError message={testEmail.error.message} />}
        <div className="settings-actions">
          <button className="button button-primary" disabled={notifications.isPending}>{notifications.isPending ? 'Saving…' : 'Save preferences'}</button>
          <button type="button" className="button quiet-button" disabled={!data.emailConfigured || testEmail.isPending} onClick={() => { setNotificationMessage(''); testEmail.mutate() }}>{testEmail.isPending ? 'Sending…' : 'Send test email'}</button>
        </div>
      </form>
    </section>
  </SettingsFrame>
}

function SettingsFrame({ children }: { children: ReactNode }) {
  return <><div className="page-heading"><div><div className="page-kicker">WORKSPACE / CONFIGURATION</div><h1>Settings</h1><p>Manage account security, display preferences, and incident email.</p></div></div><div className="settings-grid">{children}</div></>
}

function SettingsError({ message }: { message: string }) {
  return <div className="settings-inline-error" role="alert"><AlertTriangle size={14} />{message}</div>
}
