# SquadOS Multi-Node Live Demo

Two Spring Boot apps running simultaneously, talking via Redis Pub/Sub.

## Architecture

```
Terminal 1 — Node A (port 8080)        Terminal 2 — Node B (port 8081)
┌─────────────────────────┐            ┌──────────────────────────────┐
│  OracleAgent            │            │  BlitzAgent  (DPS)           │
│  (STRATEGIST)           │            │  NurseBotAgent (SUPPORT)     │
│                         │            │                              │
│  Receives user input    │            │  Subscribe to Redis channels │
│  Publishes task via     │──Redis────▶│  Process in parallel         │
│  RedisAgentBus          │◀──Redis───│  Respond with results        │
│  Collects responses     │            │                              │
│  Prints final output    │            │                              │
└─────────────────────────┘            └──────────────────────────────┘
                    └─────── Redis :6379 ──────┘
```

## Run

```bash
# 1. Start Redis
docker-compose up -d redis

# 2. Terminal 1 — start Node B (specialists) first
cd node-b
mvn spring-boot:run

# 3. Terminal 2 — start Node A (Oracle) and type your mission
cd node-a
mvn spring-boot:run
```

## What you see

**Node B (Terminal 1):**
```
[BlitzAgent]   Specialist online — waiting for tasks via Redis
[NurseBotAgent] Support online — waiting for tasks via Redis
[BlitzAgent]   Received task: Plan an attack on the north gate
[NurseBotAgent] Received task: Plan an attack on the north gate
```

**Node A (Terminal 2):**
```
[Oracle] Commander online — publishing tasks to squad via Redis
Enter mission: Plan an attack on the north gate

[Oracle] Broadcasting to squad via Redis...
[Oracle] Received from BlitzAgent:  Strike fast, flank right...
[Oracle] Received from NurseBotAgent: Keep 2 units in reserve...
[Oracle] Mission brief assembled.
```

## Watch messages flow in real-time

Open Redis Insights at http://localhost:5540 and subscribe to:
  `squad:multi-node:*`
