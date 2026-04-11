# SentinelAI — Social Media Mention Analyser

> Enterprise-grade AI-powered social media monitoring platform built with **SquadOS v3.4.0**.
> Monitor mentions, analyse sentiment, auto-reply, create tickets, and track trends in real-time.

## Architecture

```
sentinel-ai/
├── sentinel-shared/      ← Common DTOs, models, enums
├── sentinel-backend/     ← Spring Boot + 7 SquadOS AI agents + REST API + WebSocket
└── sentinel-frontend/    ← React 18 + TypeScript + Recharts live dashboard
```

## 7 AI Agents (SquadOS-powered)

| Agent | Role | Responsibility |
|-------|------|----------------|
| **MonitorAgent** | STRATEGIST | Orchestrates pipeline, @OnEvent, @Delegate routing |
| **SentimentAgent** | ANALYST | Multi-dim sentiment: label + emotion + urgency + topic |
| **TrendAgent** | RESEARCHER | Pattern detection, brand health scoring, viral risk |
| **ReplyAgent** | SUPPORT | Brand-safe auto-reply generation, @Improve learning |
| **TicketAgent** | SUPPORT | CRM ticket creation with full context |
| **EscalationAgent** | CRITIC | P1/P2/P3 priority scoring, SLA assignment |
| **ComplianceAgent** | CRITIC | Brand voice + regulatory compliance check |

## Processing Pipeline

```
Twitter/X mention arrives
    │
    ▼ @OnEvent(topic="mention.incoming")
MonitorAgent — triage + @Delegate routing
    │
    ▼ @SquadPlan
SentimentAgent — POSITIVE/NEGATIVE/NEUTRAL + emotion + urgency + topic
    │
    ▼ @SquadPlan
EscalationAgent — P1/P2/P3 + SLA hours + escalation path
    │
    ├── @SquadPlan (all mentions)
    │   ReplyAgent — generate brand-safe response
    │   ComplianceAgent — review for regulatory compliance
    │   → @AwaitApproval → human review queue
    │
    └── @SquadPlan (NEGATIVE / P1 / P2)
        TicketAgent — create CRM ticket with full context
        → TicketConnector → Zendesk/Jira/Freshdesk/MOCK
```

## Quick Start

### Option 1 — Docker Compose (recommended)
```bash
cd sentinel-ai
docker-compose up -d
open http://localhost:3000
```

### Option 2 — Local Development
```bash
# Prerequisites
ollama pull llama3.2
docker run -d -p 6379:6379 redis:7-alpine

# Terminal 1 — Backend
cd sentinel-ai
mvn clean install -DskipTests
cd sentinel-backend
mvn spring-boot:run
# API: http://localhost:8090

# Terminal 2 — Frontend
cd sentinel-frontend
npm install
npm run dev
# Dashboard: http://localhost:3000
```

### Option 3 — With Alternative LLM Providers

```bash
# Using OpenAI (GPT-4)
cd sentinel-ai/sentinel-backend
export LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-your-key-here
mvn spring-boot:run

# Using Anthropic (Claude)
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-your-key-here
mvn spring-boot:run

# Using Google Gemini
export LLM_PROVIDER=gemini
export VERTEX_AI_PROJECT_ID=your-project-id
mvn spring-boot:run
```

## Dashboard Features

| Tab | Features |
|-----|---------|
| **Overview** | Brand health gauge, sentiment trend chart, recent mentions |
| **Mention Feed** | Real-time filtered mention browser with full analysis |
| **Analytics** | Sentiment distribution pie, 24h stacked bar trend |
| **Tickets** | Ticket pipeline table, one-click resolve |
| **Reply Queue** | Pending reply approvals with Approve/Reject buttons |
| **Alerts** | Critical P1 mentions requiring immediate action |
| **Test** | Inject custom mentions for testing the AI pipeline |

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/mentions | List all mentions with filters |
| POST | /api/mentions/ingest | Submit a mention for analysis |
| POST | /api/mentions/{id}/reply/approve | Approve AI-generated reply |
| POST | /api/mentions/{id}/reply/reject | Reject reply (with revision) |
| GET | /api/analytics/summary | 24h analytics summary |
| GET | /api/analytics/trend | Hourly sentiment trend data |
| GET | /api/analytics/health | Brand health score |
| GET | /api/tickets | All CRM tickets |
| POST | /api/tickets/{id}/resolve | Resolve a ticket |
| GET | /api/pending-replies | Mentions awaiting reply approval |
| GET | /api/alerts | Critical alerts (last 1h) |

## WebSocket

```
ws://localhost:8090/ws/mentions

Events:
  { type: "mention.new",       data: Mention }  — new mention ingested
  { type: "mention.processed", data: Mention }  — analysis complete
  { type: "mention.error",     data: Mention }  — processing failed
```

## Configuration

### LLM Provider Setup

SentinelAI supports **4 LLM providers** out of the box:

| Provider | Setup | Cost |
|----------|-------|------|
| **Ollama** (default) | `ollama pull llama3.2` | Free, local |
| **OpenAI** | `OPENAI_API_KEY=sk-...` | $0.03-$0.06 per 1K tokens |
| **Anthropic (Claude)** | `ANTHROPIC_API_KEY=sk-ant-...` | $0.003-$0.024 per 1K tokens |
| **Google Gemini** | `VERTEX_AI_PROJECT_ID=...` | $0.00075-$0.003 per 1K tokens |

### Configure Your LLM Provider

```bash
# Option 1: Use Ollama (default, free)
# Already configured, just ensure ollama is running
ollama serve

# Option 2: Use OpenAI
export LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-your-key-here
mvn spring-boot:run

# Option 3: Use Anthropic/Claude
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-your-key-here
mvn spring-boot:run

# Option 4: Use Google Gemini
export LLM_PROVIDER=gemini
export VERTEX_AI_PROJECT_ID=your-project-id
export VERTEX_AI_LOCATION=us-central1
mvn spring-boot:run
```

### Backend Configuration

```properties
# sentinel-backend/src/main/resources/application.properties

# LLM Settings
squad.llm.provider=${LLM_PROVIDER:ollama}  # switch between providers
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.openai.chat.options.model=gpt-4
spring.ai.anthropic.chat.options.model=claude-3-sonnet-20240229

# SentinelAI Settings
sentinel.handle=@AirtelPaymentsBank      # monitored handle
sentinel.mock.enabled=true               # use mock data (set false for real Twitter API)
sentinel.polling.interval-ms=30000       # how often to ingest mock mentions
sentinel.auto-reply.enabled=true         # generate AI replies
sentinel.auto-reply.require-approval=true # require human approval before posting
sentinel.ticket.system=MOCK              # MOCK / ZENDESK / JIRA / FRESHDESK
sentinel.brand.name=Airtel Payments Bank
sentinel.brand.tone=professional,empathetic,solution-focused
```

### Social Media Platform Setup

#### Twitter/X API v2 (Real-time Streaming + Polling)
```bash
# Get Bearer Token from: https://developer.twitter.com/en/portal/dashboard
export TWITTER_BEARER_TOKEN=AAAA...

# Configure in application.properties
sentinel.twitter.enabled=true
sentinel.twitter.bearer-token=${TWITTER_BEARER_TOKEN}
sentinel.twitter.stream-enabled=true  # for real-time or false for polling
```

#### Facebook Graph API
```bash
export FACEBOOK_ACCESS_TOKEN=your_token
sentinel.facebook.enabled=true
sentinel.facebook.access-token=${FACEBOOK_ACCESS_TOKEN}
```

#### Instagram Basic Display API
```bash
export INSTAGRAM_ACCESS_TOKEN=your_token
sentinel.instagram.enabled=true
sentinel.instagram.access-token=${INSTAGRAM_ACCESS_TOKEN}
```

#### LinkedIn Marketing API
```bash
export LINKEDIN_ACCESS_TOKEN=your_token
sentinel.linkedin.enabled=true
sentinel.linkedin.access-token=${LINKEDIN_ACCESS_TOKEN}
```

## CRM Integration

To connect real Zendesk/Jira, extend `TicketConnectorService`:

```java
// Zendesk example
POST https://yourcompany.zendesk.com/api/v2/tickets.json
Authorization: Basic {base64(email:token)}
{
  "ticket": {
    "subject": "{ticketPayload.title}",
    "comment": { "body": "{ticketPayload.description}" },
    "priority": "urgent",
    "tags": {ticketPayload.tags}
  }
}
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| AI Agents | SquadOS v3.4.0 (7 agents, all 15 annotations) |
| Backend | Spring Boot 3.3 + WebSocket + H2/PostgreSQL |
| LLM | **Multi-provider support**: Ollama (local), OpenAI (GPT-4), Anthropic (Claude), Google Gemini |
| Social Media | Twitter/X API v2 (streaming + polling), Facebook, Instagram, LinkedIn |
| Frontend | React 18 + TypeScript + Recharts 2.8 + Vite |
| Real-time | WebSocket (Spring) + React hooks |
| Shared Cache | Redis (optional — for multi-instance tracing) |
| Containers | Docker + Docker Compose |

## SquadOS Annotations Used

All 15 SquadOS annotations in production:
`@Agent` `@SquadApplication` `@PostConstruct` `@OnEvent` `@OnMessage`
`@MissionProfile` `@SquadPlan` `@Required` `@SquadTool` `@Delegate`
`@AwaitApproval` `@AutoApproval` `@Traced` `@Improve` `@SecureAgent`

## Recent Improvements (v3.4.0+)

### ✅ Robust JSON Deserialization
- **Issue Fixed**: NullPointerException when LLM returns null responses
- **Solution**: Added null/empty validation in `SquadPlanDeserialiser` with meaningful error messages
- **Impact**: AI pipeline gracefully handles Ollama timeouts and connection issues

### ✅ Multi-Platform Social Media Support
- **Platforms**: Twitter/X, Facebook, Instagram, LinkedIn (in addition to mock data)
- **Mock Ingestion**: Generates test mentions from all 4 platforms for comprehensive testing
- **Real-time Streaming**: Twitter API v2 filtered stream + fallback polling
- **Configuration**: Enable/disable platforms independently

### ✅ Multi-LLM Provider Support
- **Providers**: Ollama (local), OpenAI (GPT-4), Anthropic (Claude), Google Gemini
- **Switching**: Via `LLM_PROVIDER` environment variable at runtime
- **Cost Optimization**: Choose provider based on performance/cost tradeoff
- **Fallback**: Automatically falls back to Ollama if provider unavailable

