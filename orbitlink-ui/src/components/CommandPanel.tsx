import { useState } from 'react'
import { api, type CommandArgument, type CommandDefinition, type CommandOutcome } from '../api'
import { usePolling } from '../usePolling'
import { Panel } from './TelemetryPanel'

/**
 * Command console.
 *
 * <p>The form is built from the command definitions the server returns, so the
 * available commands, their arguments and their ranges all come from the
 * active dictionary. Hard-coding a form per command would mean the UI silently
 * going out of date the moment a new dictionary version is loaded.
 *
 * <p>Client-side constraints (required, min/max, enum options) are applied as
 * input attributes for immediate feedback, but the server validates
 * independently and is the authority. The browser check is a convenience, never
 * the gate — the API is reachable without it.
 */
export function CommandPanel() {
  const { data, loading } = usePolling(api.commands, 10000)
  const [selected, setSelected] = useState<string>('')
  const [values, setValues] = useState<Record<string, string>>({})
  const [confirmed, setConfirmed] = useState(false)
  const [outcome, setOutcome] = useState<CommandOutcome | null>(null)
  const [sending, setSending] = useState(false)

  const commands = data ?? []
  const command = commands.find((c) => c.mnemonic === selected)

  function pick(mnemonic: string) {
    setSelected(mnemonic)
    setValues({})
    setConfirmed(false)
    setOutcome(null)
  }

  async function send() {
    if (!command) return
    setSending(true)
    try {
      setOutcome(
        await api.submitCommand(command.mnemonic, coerce(command, values), 'operator', confirmed),
      )
    } catch (e) {
      setOutcome({
        accepted: false,
        mnemonic: command.mnemonic,
        errors: [e instanceof Error ? e.message : String(e)],
        logId: null,
      })
    } finally {
      setSending(false)
    }
  }

  if (loading) return <Panel title="Commands"><p className="muted">Loading…</p></Panel>

  return (
    <Panel title="Commands" count={commands.length}>
      <select className="select" value={selected} onChange={(e) => pick(e.target.value)}>
        <option value="">Select a command…</option>
        {commands.map((c) => (
          <option key={c.mnemonic} value={c.mnemonic}>
            {c.mnemonic} — {c.name}
            {c.hazardous ? '  [hazardous]' : ''}
          </option>
        ))}
      </select>

      {command && (
        <div className="command-form">
          {command.description && <p className="muted">{command.description}</p>}

          {command.arguments.map((argument) => (
            <ArgumentField
              key={argument.name}
              argument={argument}
              value={values[argument.name] ?? ''}
              onChange={(v) => setValues({ ...values, [argument.name]: v })}
            />
          ))}

          {command.hazardous && (
            <label className="confirm">
              <input
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              <span>
                This command is <strong>hazardous</strong> and is hard to undo without a ground
                pass. Confirm to send.
              </span>
            </label>
          )}

          <button className="send" onClick={() => void send()} disabled={sending}>
            {sending ? 'Sending…' : `Send ${command.mnemonic}`}
          </button>

          {outcome && (
            <div className={outcome.accepted ? 'outcome ok' : 'outcome bad'}>
              {outcome.accepted ? (
                <>Accepted — logged as #{outcome.logId}</>
              ) : (
                <>
                  <strong>Rejected</strong>
                  <ul>
                    {outcome.errors.map((error) => (
                      <li key={error}>{error}</li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}
        </div>
      )}

      <CommandLog />
    </Panel>
  )
}

function ArgumentField({
  argument,
  value,
  onChange,
}: {
  argument: CommandArgument
  value: string
  onChange: (value: string) => void
}) {
  const label = (
    <span className="field-label">
      {argument.name}
      {!argument.required && <span className="muted"> (optional)</span>}
      {argument.minValue !== null && argument.maxValue !== null && (
        <span className="muted">
          {' '}
          {argument.minValue}–{argument.maxValue}
        </span>
      )}
    </span>
  )

  if (argument.dataType === 'BOOLEAN') {
    return (
      <label className="field">
        {label}
        <select className="select" value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
      </label>
    )
  }

  if (argument.dataType === 'ENUM') {
    return (
      <label className="field">
        {label}
        <select className="select" value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          {argument.allowedValues.map((allowed) => (
            <option key={allowed} value={allowed}>
              {allowed}
            </option>
          ))}
        </select>
      </label>
    )
  }

  const numeric = argument.dataType === 'INTEGER' || argument.dataType === 'FLOAT'
  return (
    <label className="field">
      {label}
      <input
        className="input"
        type={numeric ? 'number' : 'text'}
        step={argument.dataType === 'INTEGER' ? 1 : 'any'}
        min={argument.minValue ?? undefined}
        max={argument.maxValue ?? undefined}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={argument.description ?? ''}
      />
    </label>
  )
}

/**
 * Converts form strings to the JSON types the server expects.
 *
 * <p>Every input is a string in the DOM, but the server deliberately refuses to
 * coerce — `"false"` for a boolean is an error there, because the usual
 * non-empty-string convention would turn it into true. So the conversion has to
 * happen here, and an empty field is omitted entirely rather than sent as ""
 * so that optional arguments stay genuinely absent.
 */
function coerce(
  command: CommandDefinition,
  values: Record<string, string>,
): Record<string, unknown> {
  const out: Record<string, unknown> = {}

  for (const argument of command.arguments) {
    const raw = values[argument.name]
    if (raw === undefined || raw === '') continue

    switch (argument.dataType) {
      case 'BOOLEAN':
        out[argument.name] = raw === 'true'
        break
      case 'INTEGER':
      case 'FLOAT': {
        const parsed = Number(raw)
        // Pass a non-numeric string through unchanged and let the server
        // reject it with its own message, rather than sending NaN.
        out[argument.name] = Number.isNaN(parsed) ? raw : parsed
        break
      }
      default:
        out[argument.name] = raw
    }
  }
  return out
}

function CommandLog() {
  const { data } = usePolling(() => api.commandLog(10), 4000)
  const entries = data ?? []

  if (entries.length === 0) return null

  return (
    <div className="command-log">
      <h3>Recent commands</h3>
      <ul>
        {entries.map((entry) => (
          <li key={entry.id}>
            <span className={`chip chip-${entry.status === 'ACCEPTED' ? 'ok' : 'critical'}`}>
              {entry.status}
            </span>
            <span className="mnemonic">{entry.mnemonic}</span>
            <span className="muted">{new Date(entry.issuedAt).toLocaleTimeString()}</span>
            {entry.rejectionReason && (
              <div className="muted reason">{entry.rejectionReason}</div>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
