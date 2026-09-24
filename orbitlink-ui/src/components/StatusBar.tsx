import { api } from '../api'
import { usePolling } from '../usePolling'

export function StatusBar() {
  const { data, error } = usePolling(api.status, 2000)

  return (
    <header className="status-bar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true" />
        <span className="brand-name">OrbitLink</span>
        <span className="brand-sub">Telemetry &amp; Command</span>
      </div>

      <div className="status-items">
        <Stat
          label="Link"
          value={data?.listening ? `UP :${data.port}` : 'DOWN'}
          tone={data?.listening ? 'good' : 'bad'}
        />
        <Stat label="Dictionary" value={data?.activeDictionary ?? 'none'} />
        <Stat label="Packets" value={fmt(data?.packetsReceived)} />
        <Stat
          label="Rejected"
          value={fmt(data?.packetsRejected)}
          tone={(data?.packetsRejected ?? 0) > 0 ? 'warn' : undefined}
        />
        <Stat label="Samples" value={fmt(data?.storedSamples)} />
      </div>

      {error && <div className="status-error" role="status">server unreachable — showing last known values</div>}
    </header>
  )
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone?: 'good' | 'warn' | 'bad'
}) {
  return (
    <div className="stat">
      <span className="stat-label">{label}</span>
      <span className={`stat-value${tone ? ` tone-${tone}` : ''}`}>{value}</span>
    </div>
  )
}

function fmt(value: number | undefined): string {
  return value === undefined ? '—' : value.toLocaleString()
}
