# squad-dashboard-ui

React + TypeScript dashboard for SquadOS — real-time monitoring of all agent activity.

## Features

| Tab | What you see |
|-----|-------------|
| **Overview** | KPI stats, calls/tokens/latency area charts, error pie, top-5 agents bar |
| **Agents** | All registered agents with metrics, success bar, radar performance chart |
| **Traces** | 200 agent spans, filterable by status/name, full span detail panel |
| **Metrics** | Per-agent calls vs errors, token usage, latency, call-rate time series |
| **Approvals** | Approval queue with one-click Approve / Reject, live count badges |
| **Workflows** | Durable workflow states (RUNNING/PAUSED/COMPLETED/FAILED), step timeline |
| **Security** | Guardrail blocks, PII redactions, injection detections, severity filters |
| **Activity** | Live SSE event stream with animated connection indicator |

## Quick start

```bash
# 1. Start the API backend
cd ../squad-dashboard-api
mvn spring-boot:run   # → http://localhost:8090

# 2. Start the UI dev server
cd ../squad-dashboard-ui
npm install
npm run dev           # → http://localhost:5173
```

The Vite dev server proxies `/api/*` to `http://localhost:8090`.

## Production build

```bash
npm run build   # output → dist/
```

Serve `dist/` with any static host (Nginx, Netlify, Vercel…).  
Point your CDN/server to proxy `/api` to `http://your-dashboard-api:8090`.

## Tech stack

- **React 18** + TypeScript
- **Recharts 2** — AreaChart, BarChart, LineChart, PieChart, RadarChart
- **Tailwind CSS 3** — dark theme, utility-first
- **Vite 5** — HMR, TypeScript, fast builds
- **SSE** — native `EventSource` for live activity stream
