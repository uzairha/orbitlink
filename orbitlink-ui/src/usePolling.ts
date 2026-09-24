import { useCallback, useEffect, useRef, useState } from 'react'

export interface Polled<T> {
  data: T | null
  error: string | null
  /** True only for the first load, so a refresh does not blank the screen. */
  loading: boolean
  refresh: () => void
}

/**
 * Polls an endpoint on an interval.
 *
 * <p>Polling rather than websockets: telemetry updates a few times a second at
 * most and the operator view is a snapshot, so a 2s poll is simpler and has no
 * reconnection logic to get wrong. A live plot would justify a websocket; a
 * table of current values does not.
 */
export function usePolling<T>(fetcher: () => Promise<T>, intervalMs = 2000): Polled<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // Held in a ref so changing the fetcher identity between renders does not
  // tear down and recreate the interval on every render.
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const load = useCallback(async (cancelled?: () => boolean) => {
    try {
      const result = await fetcherRef.current()
      if (cancelled?.()) return
      setData(result)
      setError(null)
    } catch (e) {
      if (cancelled?.()) return
      // Keep the last good data on screen and show the error alongside it:
      // a blanked dashboard during a brief server restart is worse than a
      // slightly stale one that says so.
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      if (!cancelled?.()) setLoading(false)
    }
  }, [])

  useEffect(() => {
    let stopped = false
    const cancelled = () => stopped

    void load(cancelled)
    const timer = setInterval(() => void load(cancelled), intervalMs)

    return () => {
      stopped = true
      clearInterval(timer)
    }
  }, [load, intervalMs])

  return { data, error, loading, refresh: () => void load() }
}
