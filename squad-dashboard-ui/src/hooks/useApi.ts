import { useState, useEffect, useCallback } from 'react'

export function useApi<T>(
  fetcher: () => Promise<T>,
  interval: number = 5000
): { data: T | null; loading: boolean; error: string | null; refetch: () => void } {
  const [data, setData]       = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const result = await fetcher()
      setData(result)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }, [fetcher])

  useEffect(() => {
    load()
    if (interval > 0) {
      const id = setInterval(load, interval)
      return () => clearInterval(id)
    }
  }, [load, interval])

  return { data, loading, error, refetch: load }
}
