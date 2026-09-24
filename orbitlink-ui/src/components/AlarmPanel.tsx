import { useState } from 'react'
import { api, type Alarm } from '../api'
import { usePolling } from '../usePolling'
import { Panel } from './TelemetryPanel'

export function AlarmPanel({ alarms }: { alarms: Alarm[] }) {
  const [showHistory, setShowHistory] = useState(false)
  const history = usePolling(api.alarmHistory, 5000)

  const shown = showHistory ? history.data ?? [] : alarms

  return (
    <Panel title="Alarms" count={alarms.length}>
      <div className="toggle-row">
        <button
          className={!showHistory ? 'toggle on' : 'toggle'}
          onClick={() => setShowHistory(false)}
        >
          Active
        </button>
        <button
          className={showHistory ? 'toggle on' : 'toggle'}
          onClick={() => setShowHistory(true)}
        >
          History
        </button>
      </div>

      {shown.length === 0 ? (
        <p className="muted">
          {showHistory ? 'No alarms recorded.' : 'No active alarms — all parameters within limits.'}
        </p>
      ) : (
        <ul className="alarm-list">
          {shown.map((alarm) => (
            <li key={alarm.id} className={`alarm alarm-${alarm.severity.toLowerCase()}`}>
              <div className="alarm-head">
                <span className={`chip chip-${alarm.severity.toLowerCase()}`}>
                  {alarm.severity}
                </span>
                <span className="mnemonic">{alarm.mnemonic}</span>
                {!alarm.active && <span className="chip chip-cleared">CLEARED</span>}
              </div>

              <div className="alarm-detail">
                {alarm.violatedLimit.replace('_', ' ').toLowerCase()} limit {fmt(alarm.limitValue)}
                {alarm.units ? ` ${alarm.units}` : ''} — peaked at{' '}
                <strong>{fmt(alarm.peakValue)}</strong>
                {alarm.units ? ` ${alarm.units}` : ''}
              </div>

              <div className="alarm-meta muted">
                {alarm.sampleCount} sample{alarm.sampleCount === 1 ? '' : 's'} ·{' '}
                {duration(alarm.durationSeconds)} · raised{' '}
                {new Date(alarm.raisedAt).toLocaleTimeString()}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

function fmt(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

function duration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}
