/**
 * Typed client for the OrbitLink REST API.
 *
 * These interfaces mirror the server's response records by hand. Nothing
 * enforces that they stay in step — the honest alternative would be generating
 * them from an OpenAPI schema, which is the right move once the API stops
 * changing every phase.
 */

export interface IngestionStatus {
  listening: boolean
  port: number
  packetsReceived: number
  packetsRejected: number
  activeDictionary: string | null
  storedSamples: number
}

export interface Sample {
  mnemonic: string
  name: string
  units: string | null
  rawValue: number
  engValue: number
  receivedAt: string
}

export type Severity = 'OK' | 'WARNING' | 'CRITICAL'

export interface Alarm {
  id: number
  mnemonic: string
  parameterName: string
  units: string | null
  severity: Severity
  violatedLimit: string
  limitValue: number
  triggeringValue: number
  peakValue: number
  sampleCount: number
  raisedAt: string
  clearedAt: string | null
  active: boolean
  durationSeconds: number
}

export interface CommandArgument {
  name: string
  description: string | null
  dataType: 'INTEGER' | 'FLOAT' | 'BOOLEAN' | 'ENUM' | 'STRING'
  position: number
  minValue: number | null
  maxValue: number | null
  allowedValues: string[]
  required: boolean
}

export interface CommandDefinition {
  mnemonic: string
  name: string
  description: string | null
  apid: number
  functionCode: number
  hazardous: boolean
  arguments: CommandArgument[]
}

export interface CommandOutcome {
  accepted: boolean
  mnemonic: string
  errors: string[]
  logId: number | null
}

export interface CommandLogEntry {
  id: number
  mnemonic: string
  issuedAt: string
  issuedBy: string
  arguments: string
  status: 'ACCEPTED' | 'REJECTED'
  rejectionReason: string | null
}

export interface DictionarySummary {
  version: string
  description: string | null
  active: boolean
  parameterCount: number
}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) {
    throw new Error(`${path} returned ${response.status}`)
  }
  return response.json() as Promise<T>
}

export const api = {
  status: () => get<IngestionStatus>('/api/v1/telemetry/status'),
  latest: () => get<Sample[]>('/api/v1/telemetry/latest'),
  activeAlarms: () => get<Alarm[]>('/api/v1/alarms'),
  alarmHistory: (limit = 25) => get<Alarm[]>(`/api/v1/alarms/history?limit=${limit}`),
  commands: () => get<CommandDefinition[]>('/api/v1/commands'),
  commandLog: (limit = 25) => get<CommandLogEntry[]>(`/api/v1/commands/log?limit=${limit}`),
  dictionaries: () => get<DictionarySummary[]>('/api/v1/dictionaries'),

  /**
   * A rejected command answers 422 with a body describing why, so a non-OK
   * response is parsed rather than thrown. Only a genuinely unexpected status
   * is an error.
   */
  async submitCommand(
    mnemonic: string,
    args: Record<string, unknown>,
    issuedBy: string,
    confirmed: boolean,
  ): Promise<CommandOutcome> {
    const response = await fetch('/api/v1/commands', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mnemonic, arguments: args, issuedBy, confirmed }),
    })
    if (response.status === 202 || response.status === 422) {
      return response.json() as Promise<CommandOutcome>
    }
    throw new Error(`command submission returned ${response.status}`)
  },
}
