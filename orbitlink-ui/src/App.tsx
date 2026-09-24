import { api } from './api'
import { AlarmPanel } from './components/AlarmPanel'
import { CommandPanel } from './components/CommandPanel'
import { StatusBar } from './components/StatusBar'
import { TelemetryPanel } from './components/TelemetryPanel'
import { usePolling } from './usePolling'
import './App.css'

export default function App() {
  // Alarms are fetched once here and passed down rather than polled separately
  // by both panels. Two independent polls would drift out of step and briefly
  // show a parameter coloured red while the alarm list said it had cleared.
  const { data: alarms } = usePolling(api.activeAlarms, 2000)
  const active = alarms ?? []

  return (
    <div className="app">
      <StatusBar />
      <main className="grid">
        <TelemetryPanel alarms={active} />
        <div className="column">
          <AlarmPanel alarms={active} />
          <CommandPanel />
        </div>
      </main>
      <footer className="footer">OrbitLink ground system · polled every 2s</footer>
    </div>
  )
}
