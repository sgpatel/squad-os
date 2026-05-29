const BASE = '/api/v1'

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

export const api = {
  agents:    ()                => get('/agents'),
  traces:    (limit = 200)     => get(`/traces?limit=${limit}`),
  metrics:   ()                => get('/metrics'),
  workflows: (state?: string)  => get(`/workflows${state ? `?state=${state}` : ''}`),
  security:  (limit = 100)     => get(`/security?limit=${limit}`),
  health:    ()                => get('/health'),
  activity:  (limit = 100)     => get(`/activity?limit=${limit}`),
}

