import { useState, useEffect } from 'react'
import { sseStream } from '../utils/api'
import type { ActivityEvent } from '../types'

const MAX = 200

export function useSSE(): { events: ActivityEvent[]; connected: boolean } {
  const [events, setEvents]       = useState<ActivityEvent[]>([])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    const source = new EventSource('/api/v1/activity/stream')

    source.addEventListener('activity', (e: MessageEvent) => {
      try {
        const ev: ActivityEvent = JSON.parse(e.data)
        setEvents(prev => [ev, ...prev].slice(0, MAX))
        setConnected(true)
      } catch {/* ignore malformed */}
    })

    source.onopen  = () => setConnected(true)
    source.onerror = () => setConnected(false)

    return () => { source.close(); setConnected(false) }
  }, [])

  return { events, connected }
}
