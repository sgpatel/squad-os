# SquadOS Dashboard

> Live monitoring dashboard for SquadOS agent squads.
> Built with React 18 + Recharts 2.8 + JetBrains Mono.

## Features

- **7 tabs**: Overview, Agents, Traces, Votes, Approvals, Security, Improve
- **8 live charts**: Pie charts (votes, status, roles, feedback), Bar charts (latency, duration, vote breakdown)
- **Agent health cards** with role-coloured borders and temp/token stats
- **Vote history** with progress bars showing approve/reject split
- **Approval queue** with one-click Approve/Reject buttons
- **Audit log** with search/filter (@SecureAgent decisions)
- **Feedback store** with GOOD/BAD labels (@Improve examples)
- **Shared Redis trace store** — shows spans from ANY SquadOS JVM
- **Auto-refresh** every 5 seconds
- Dark navy theme with neon role colours

## Architecture

```
fraud-detection JVM          dashboard JVM (:8080)
       │                            │
       ▼ RedisTraceExporter          ▼ RedisTraceExporter
  LPUSH squados:traces    ←→   LRANGE squados:traces
  INCRBY squados:traces:tokens  GET squados:traces:tokens
```

## Run

```bash
# 1. Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# 2. Run fraud-detection (produces spans)
cd squad-examples/fraud-detection && mvn spring-boot:run

# 3. Run dashboard (reads spans)
cd squad-dashboard && mvn spring-boot:run

# 4. Open browser
open http://localhost:8080
```

## REST API

| Endpoint | Description |
|----------|-------------|
| `GET /api/status` | Squad health summary |
| `GET /api/agents` | Registered agents |
| `GET /api/traces` | Recent trace spans |
| `GET /api/traces/summary` | Token + latency summary |
| `GET /api/votes` | Vote history |
| `GET /api/approvals` | Approval queue |
| `POST /api/approvals/{id}/approve` | Approve a pending request |
| `POST /api/approvals/{id}/reject` | Reject a pending request |
| `GET /api/feedback` | @Improve feedback examples |
| `GET /api/audit` | @SecureAgent audit log |
| `GET /api/activity` | Live activity feed |

## Tech Stack

- **React 18** — component tree, hooks
- **Recharts 2.8** — charts via unpkg CDN
- **JetBrains Mono** — code font for spans/tokens
- **Inter** — UI font
- **Spring Boot 3.3** — REST API backend
- **squad-spring-boot-starter** — auto-configured SquadContext
- **RedisTraceExporter** — shared multi-JVM trace store
