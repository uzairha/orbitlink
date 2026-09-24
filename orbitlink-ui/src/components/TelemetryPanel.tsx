import { api, type Alarm, type Sample } from '../api'
import { usePolling } from '../usePolling'

/**
 * Current value of every parameter, coloured by whether it is in alarm.
 *
 * <p>The severity comes from the alarms endpoint rather than being recomputed
 * in the browser. Re-deriving it here would mean shipping the limits to the
 * client and duplicating the comparison logic — and the two copies would
 * eventually disagree, leaving the display contradicting the alarm list.
 */
export function TelemetryPanel({ alarms }: { alarms: Alarm[] }) {
  const { data, error, loading } = usePolling(api.latest, 2000)

  const severityByMnemonic = new Map(alarms.map((alarm) => [alarm.mnemonic, alarm.severity]))

  if (loading) return <Panel title="Telemetry"><p className="muted">Waiting for telemetry…</p></Panel>

  const samples = data ?? []

  return (
    <Panel title="Telemetry" count={samples.length}>
      {error && <p className="muted">stale — {error}</p>}
      {samples.length === 0 ? (
        <p className="muted">
          No samples yet. Start the simulator to downlink telemetry.
        </p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Parameter</th>
              <th className="num">Value</th>
              <th>Units</th>
              <th className="num">Raw</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {samples.map((sample) => (
              <Row
                key={sample.mnemonic}
                sample={sample}
                severity={severityByMnemonic.get(sample.mnemonic)}
              />
            ))}
          </tbody>
        </table>
      )}
    </Panel>
  )
}

function Row({ sample, severity }: { sample: Sample; severity?: string }) {
  const tone = severity === 'CRITICAL' ? 'bad' : severity === 'WARNING' ? 'warn' : undefined

  return (
    <tr className={tone ? `row-${tone}` : undefined}>
      <td>
        <span className="mnemonic">{sample.mnemonic}</span>
        <span className="param-name">{sample.name}</span>
      </td>
      <td className={`num value${tone ? ` tone-${tone}` : ''}`}>{format(sample.engValue)}</td>
      <td className="units">{sample.units ?? ''}</td>
      <td className="num muted">{sample.rawValue}</td>
      <td className="muted">{time(sample.receivedAt)}</td>
    </tr>
  )
}

/** Three significant decimals is enough for every parameter in the dictionary. */
function format(value: number): string {
  if (Number.isInteger(value)) return String(value)
  return value.toFixed(3).replace(/\.?0+$/, '')
}

function time(iso: string): string {
  return new Date(iso).toLocaleTimeString()
}

export function Panel({
  title,
  count,
  children,
}: {
  title: string
  count?: number
  children: React.ReactNode
}) {
  return (
    <section className="panel">
      <h2>
        {title}
        {count !== undefined && <span className="badge">{count}</span>}
      </h2>
      {children}
    </section>
  )
}
