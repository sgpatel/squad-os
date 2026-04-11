# SentinelAI — Architecture Design Document

> **Classification:** Internal Technical Design  
> **Version:** 1.0.0  
> **Status:** Production (Release Branch)  
> **Authors:** Platform Architecture Team  
> **Last Updated:** March 2026

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Business Context](#2-business-context)
3. [System Overview](#3-system-overview)
4. [Architecture Principles](#4-architecture-principles)
5. [High-Level Architecture](#5-high-level-architecture)
6. [Module Design](#6-module-design)
   - 6.1 [sentinel-shared](#61-sentinel-shared)
   - 6.2 [sentinel-backend](#62-sentinel-backend)
   - 6.3 [sentinel-frontend](#63-sentinel-frontend)
7. [AI Agent Architecture](#7-ai-agent-architecture)
8. [Data Architecture](#8-data-architecture)
9. [API Design](#9-api-design)
10. [Security Architecture](#10-security-architecture)
11. [Integration Architecture](#11-integration-architecture)
12. [Processing Pipeline](#12-processing-pipeline)
13. [Observability and Monitoring](#13-observability-and-monitoring)
14. [Deployment Architecture](#14-deployment-architecture)
15. [Scalability and Performance](#15-scalability-and-performance)
16. [Technology Decisions](#16-technology-decisions)
17. [Risk Register](#17-risk-register)
18. [Roadmap](#18-roadmap)

---

## 1. Executive Summary

SentinelAI is an **enterprise-grade, AI-powered social media mention analyser**
designed for financial services organisations. It monitors brand mentions across
social platforms in real time, analyses sentiment and urgency using a squad of
specialised AI agents, auto-generates brand-safe responses, creates CRM tickets
for issues, and surfaces actionable intelligence through a live operations
dashboard.

The system is built on **SquadOS v3.4.0** — a multi-agent AI framework for Java
— which provides the orchestration layer for seven specialist agents that
collaborate to process every mention through a fully automated pipeline.

### Key Metrics (Design Targets)

| Metric | Target |
|--------|--------|
| Mention-to-analysis latency | < 30 seconds (LLM-bound) |
| Concurrent mention processing | 10 parallel threads |
| API response time (P99) | < 200ms |
| Dashboard refresh rate | 10 seconds (mentions) / 5 seconds (alerts) |
| JWT token expiry | 24 hours |
| Ticket creation (NEGATIVE + P1/P2) | Automatic, < 5 seconds after analysis |
| Reply queue throughput | Unlimited (async, human-gated) |

---

## 2. Business Context

### Problem Statement

Financial services brands (banks, payment apps, insurance) receive hundreds to
thousands of social media mentions daily. Traditional monitoring tools:

- Only detect mentions but do not analyse or act
- Require large social media teams to read and respond manually
- Miss the urgency of P1 issues (fraud allegations, regulatory complaints)
- Have no integration with ticketing/CRM systems
- Provide no brand health trend intelligence

### Solution

SentinelAI replaces manual monitoring with an **AI-native pipeline** that:

1. Ingests mentions from social platforms in real time
2. Classifies sentiment, emotion, urgency, and category automatically
3. Generates brand-safe, compliance-checked replies
4. Creates CRM tickets for negative/critical mentions
5. Escalates P1 issues (viral risk, fraud, regulatory) immediately
6. Provides a live dashboard for the social media operations team

### Stakeholders

| Stakeholder | Concern |
|-------------|---------|
| Social Media Ops Team | Reply approval, mention feed, alerts |
| Customer Support Manager | Ticket volume, SLA compliance |
| Brand/PR Team | Brand health score, sentiment trends |
| Compliance Officer | Regulatory mention detection, reply review |
| CTO / Engineering | API reliability, scalability, LLM cost |
| CEO / Marketing | Brand health KPIs, crisis detection |

---

## 3. System Overview

```
╔══════════════════════════════════════════════════════════════════════╗
║                         External Sources                             ║
║   Twitter/X API v2    LinkedIn    App Store    News Feeds            ║
╚══════════════════╤═══════════════════════════════════════════════════╝
                   │  Mentions (streaming + polling)
                   ▼
╔══════════════════════════════════════════════════════════════════════╗
║                    SentinelAI Backend  (:8090)                       ║
║                                                                      ║
║  ┌─────────────────────────────────────────────────────────────┐     ║
║  │                  Ingestion Layer                            │     ║
║  │  TwitterMentionIngestionService  MockMentionIngestionService │     ║
║  └─────────────────────┬───────────────────────────────────────┘     ║
║                        │                                             ║
║  ┌─────────────────────▼───────────────────────────────────────┐     ║
║  │              SquadOS Agent Pipeline (7 Agents)              │     ║
║  │  Monitor → Sentiment → Escalation → Reply → Compliance      │     ║
║  │                       → Ticket → Trend                      │     ║
║  └─────────────────────┬───────────────────────────────────────┘     ║
║                        │                                             ║
║  ┌─────────────────────▼───────────────────────────────────────┐     ║
║  │                Persistence Layer                            │     ║
║  │       H2 (dev) / PostgreSQL (prod) via Flyway               │     ║
║  └─────────────────────────────────────────────────────────────┘     ║
║                                                                      ║
║  ┌──────────────────────────────────────────────────────────────┐    ║
║  │   REST API (11 endpoints)   WebSocket (/ws/mentions)         │    ║
║  └──────────────────────────────────────────────────────────────┘    ║
╚══════════════════╤═══════════════════════════════════════════════════╝
                   │  JSON / WebSocket
                   ▼
╔══════════════════════════════════════════════════════════════════════╗
║                 SentinelAI Frontend  (:3000)                         ║
║   React 18 · TypeScript · Recharts · Vite                            ║
║   7 tabs: Overview · Feed · Analytics · Tickets ·                    ║
║           Reply Queue · Alerts · Test                                ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## 4. Architecture Principles

| # | Principle | Rationale |
|---|-----------|-----------|
| **P1** | **Agent-first design** | Every analytical decision is made by a specialist AI agent, not by rules |
| **P2** | **Human-in-the-loop** | All auto-generated replies require human approval before posting |
| **P3** | **Separation of concerns** | `sentinel-shared` / `sentinel-backend` / `sentinel-frontend` are independent modules |
| **P4** | **Async by default** | Mention processing runs in virtual threads; never blocks the HTTP layer |
| **P5** | **Fail-safe escalation** | On any agent error, mention is marked ERROR and escalated, never silently dropped |
| **P6** | **Provider-agnostic LLM** | `LlmPort` abstraction — swap Ollama for OpenAI with zero agent changes |
| **P7** | **Config over code** | Brand handle, tone, ticket system, Twitter credentials — all via `application.properties` |
| **P8** | **Profile-based environments** | `dev` profile = H2 in-memory; `prod` profile = PostgreSQL, no mock data |
| **P9** | **Compliance first** | Every AI reply passes through `ComplianceAgent` before entering the approval queue |
| **P10** | **Observable** | Every agent call produces a traced span; SquadTracer records tokens + latency |

---

## 5. High-Level Architecture

### Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                         sentinel-frontend                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ AuthCtx  │ │useSentinel│ │  App.tsx │ │  Charts  │ │  Toast   │   │
│  │ (JWT)    │ │ (hooks)  │ │ (layout) │ │(Recharts)│ │ (notify) │   │
│  └────┬─────┘ └────┬─────┘ └──────────┘ └──────────┘ └──────────┘   │
│       │             │  REST + WebSocket                                │
└───────┼─────────────┼──────────────────────────────────────────────────┘
        │             │
        ▼             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         sentinel-backend                               │
│                                                                        │
│  ┌─────────────┐   ┌──────────────────────────────────────────────┐   │
│  │  Security   │   │              API Layer                        │   │
│  │  JWT Filter │   │  MentionController  /api/*                    │   │
│  │  Auth Ctrl  │   │  AuthController     /api/auth/*               │   │
│  │  Admin Ctrl │   │  AdminController    /api/admin/*              │   │
│  └─────────────┘   └──────────────────────────────────────────────┘   │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │                    Service Layer                                  │ │
│  │  MentionProcessingService   AnalyticsService  TicketConnector    │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │               SquadOS Agent Layer (7 Agents)                     │ │
│  │  MonitorAgent  SentimentAgent  TrendAgent  ReplyAgent            │ │
│  │  TicketAgent   EscalationAgent  ComplianceAgent                  │ │
│  └───────────────────────────┬──────────────────────────────────────┘ │
│                              │  LlmPort                               │
│  ┌───────────────────────────▼──────────────────────────────────────┐ │
│  │               SpringAiLlmAdapter → Ollama / OpenAI               │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │               Data Layer                                         │ │
│  │  MentionRepository (JPA)  UserRepository   Flyway Migrations     │ │
│  │  H2 (dev)   PostgreSQL (prod)                                    │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│  ┌───────────────┐   ┌──────────────┐   ┌────────────────────────┐   │
│  │  Ingestion    │   │  WebSocket   │   │  Connector Layer        │   │
│  │  Twitter v2   │   │  /ws/mentions│   │  Zendesk  Jira  Mock   │   │
│  │  Mock (dev)   │   │  (live push) │   │  TicketConnectorFactory │   │
│  └───────────────┘   └──────────────┘   └────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                        sentinel-shared                                 │
│  Mention  SentimentResult  TicketInfo  AnalyticsSnapshot               │
│  SentimentLabel  EmotionLabel  UrgencyLevel  MentionCategory           │
└────────────────────────────────────────────────────────────────────────┘

External:  Ollama (LLM)  ·  Redis (traces)  ·  PostgreSQL (prod)
           Zendesk / Jira  ·  Twitter API v2
```

---

## 6. Module Design

### 6.1 `sentinel-shared`

**Purpose:** Common data models shared between backend and (optionally) any
future microservices or SDKs. No business logic, no Spring dependencies.

**Package:** `io.sentinel.shared.model`

| Class | Role |
|-------|------|
| `Mention` | Core domain object — the social media post + all analysis results |
| `SentimentResult` | Multi-dimensional sentiment output from `SentimentAgent` |
| `TicketInfo` | CRM ticket metadata (ID, system, status, SLA) |
| `AnalyticsSnapshot` | Point-in-time analytics summary |
| `SentimentLabel` | Enum: `POSITIVE / NEGATIVE / NEUTRAL` |
| `EmotionLabel` | Enum: `FRUSTRATION / ANGER / SADNESS / JOY / SURPRISE / FEAR / NEUTRAL / SARCASM` |
| `UrgencyLevel` | Enum: `LOW / MEDIUM / HIGH / CRITICAL` |
| `MentionCategory` | Enum: 13 categories (PAYMENT_FAILURE, APP_CRASH, FRAUD_REPORT, …) |

**Design decisions:**
- Plain Java records/classes — no JPA annotations (kept in backend's `MentionEntity`)
- Jackson-compatible (`@JsonIgnoreProperties(ignoreUnknown = true)`)
- No transitive dependencies except `jackson-databind`

---

### 6.2 `sentinel-backend`

**Spring Boot 3.3 · Java 21 · Port 8090**

#### Package structure

```
io.sentinel.backend
├── SentinelApp.java                  ← Entry point + bean wiring
├── adapter/
│   └── SpringAiLlmAdapter.java       ← Spring AI → LlmPort bridge
├── agents/
│   ├── MonitorAgent.java             ← STRATEGIST — orchestrator
│   ├── SentimentAgent.java           ← ANALYST — sentiment analysis
│   ├── TrendAgent.java               ← RESEARCHER — trend detection
│   ├── ReplyAgent.java               ← SUPPORT — reply generation
│   ├── TicketAgent.java              ← SUPPORT — CRM ticket creation
│   ├── EscalationAgent.java          ← CRITIC — priority scoring
│   └── ComplianceAgent.java          ← CRITIC — brand compliance
├── api/
│   └── MentionController.java        ← REST API (11 endpoints)
├── config/
│   └── CorsConfig.java               ← CORS configuration
├── connector/
│   ├── TicketConnector.java          ← Interface (strategy pattern)
│   ├── ZendeskConnector.java         ← Zendesk REST v2 adapter
│   ├── JiraConnector.java            ← Jira REST v3 adapter
│   └── TicketConnectorFactory.java   ← Auto-selects enabled connector
├── ingestion/
│   ├── MentionSource.java            ← Interface for ingestion sources
│   ├── TwitterMentionIngestionService.java  ← Twitter API v2
│   └── MockMentionIngestionService.java     ← Dev/test mock data
├── repository/
│   ├── MentionEntity.java            ← JPA entity
│   └── MentionRepository.java        ← Spring Data JPA queries
├── security/
│   ├── Role.java                     ← ADMIN / ANALYST / REVIEWER / READ_ONLY
│   ├── UserEntity.java               ← JPA entity for users
│   ├── UserRepository.java           ← User data access
│   ├── JwtService.java               ← Token generation + validation
│   ├── JwtAuthFilter.java            ← OncePerRequestFilter (JWT extraction)
│   ├── SecurityConfig.java           ← Spring Security filter chain
│   ├── SentinelUserDetailsService.java ← UserDetailsService impl
│   ├── AuthController.java           ← /api/auth/* endpoints
│   ├── AdminController.java          ← /api/admin/* endpoints
│   └── DataInitializer.java          ← Seeds admin user on startup
├── service/
│   ├── MentionProcessingService.java ← Core 5-step AI pipeline
│   ├── AnalyticsService.java         ← Aggregation + brand health
│   └── TicketConnectorService.java   ← (deprecated — replaced by Factory)
└── websocket/
    └── MentionWebSocketHandler.java  ← Live push to frontend
```

#### Key design decisions

**Virtual threads for mention processing:**  
Every mention is processed in a dedicated virtual thread (`Thread.ofVirtual().start()`).
This allows 10 concurrent LLM calls without blocking the Tomcat thread pool,
which is essential because each LLM call takes 5-30 seconds.

**Strategy pattern for ticket connectors:**  
`TicketConnector` interface allows plugging in Zendesk, Jira, Freshdesk, or a
Mock connector. `TicketConnectorFactory` auto-selects the first enabled connector
and falls back to `MockTicketConnector`. Zero changes to the agent code when
switching CRM systems.

**Strategy pattern for ingestion:**  
`MentionSource` interface decouples the ingestion source from the processing
pipeline. Swap `MockMentionIngestionService` for `TwitterMentionIngestionService`
by setting `sentinel.twitter.enabled=true`.

**JPA entity vs shared model:**  
`MentionEntity` (backend) contains `@Column`, `@Entity` — JPA concerns.  
`Mention` (shared) is a clean DTO. The service layer maps between them.
This keeps shared models framework-agnostic.

---

### 6.3 `sentinel-frontend`

**React 18 · TypeScript · Vite · Recharts · Port 3000**

#### Package structure

```
sentinel-frontend/src
├── main.tsx                          ← Entry — ThemeProvider + AuthProvider + App
├── App.tsx                           ← Main dashboard (all tabs, sidebar, keyboard shortcuts)
├── index.css                         ← CSS variables, dark/light theme, animations
├── auth/
│   ├── AuthContext.tsx               ← JWT lifecycle — login, logout, session restore
│   └── LoginPage.tsx                 ← Login form
├── components/
│   ├── Toast.tsx                     ← Pop-up notifications (4-second auto-dismiss)
│   ├── Skeleton.tsx                  ← Loading shimmer placeholders
│   ├── CopyButton.tsx                ← One-click copy with confirmation
│   ├── InlineReplyEditor.tsx         ← Edit AI reply before approving
│   └── KeyboardHelp.tsx              ← ? overlay for keyboard shortcuts
├── hooks/
│   ├── useSentinel.ts                ← All API hooks + WebSocket (auth-aware)
│   ├── useKeyboard.ts                ← Global keyboard shortcut registration
│   └── useInfiniteScroll.ts          ← Intersection Observer pagination
├── theme/
│   └── ThemeContext.tsx              ← Dark/light mode with localStorage persistence
├── types/
│   └── index.ts                      ← TypeScript interfaces (Mention, Ticket, etc.)
└── utils/
    └── timeAgo.ts                    ← Human-readable time + follower count formatting
```

#### Frontend architecture decisions

**All API calls are auth-aware:**  
`authFetch()` wrapper in `useSentinel.ts` injects the `Authorization: Bearer {token}`
header on every request. On 401 response, it clears `localStorage` and reloads
to the login page — no manual session management needed.

**WebSocket for real-time updates:**  
`useLiveEvents()` maintains a persistent WebSocket connection to
`/ws/mentions`. Events (`mention.new`, `mention.processed`, `mention.error`)
are merged into the polled historical feed rather than replacing it.
Reconnect logic retries every 3 seconds on disconnect.

**CSS custom properties for theming:**  
All colors and backgrounds are CSS variables (`--bg`, `--border`, `--text`).
Dark/light mode is toggled by setting `data-theme` on `document.body`,
which overrides the variable values. No class-name toggling needed.

**Vite dev proxy:**  
```
/api  → http://localhost:8090
/ws   → ws://localhost:8090
```
In production, Nginx proxies both paths to the backend container.

---

## 7. AI Agent Architecture

### The Squad

SentinelAI is powered by seven SquadOS agents, each with a specific role in
the `AgentRole` taxonomy:

```
                    ┌───────────────────────┐
                    │     MonitorAgent       │  STRATEGIST
                    │  @OnEvent @Delegate    │
                    └──────────┬────────────┘
                               │ orchestrates
           ┌───────────────────┼─────────────────────┐
           ▼                   ▼                      ▼
  ┌────────────────┐  ┌────────────────┐   ┌────────────────┐
  │ SentimentAgent │  │  TrendAgent    │   │EscalationAgent │
  │   ANALYST      │  │  RESEARCHER    │   │   CRITIC       │
  │ @SquadPlan     │  │ @SquadPlan     │   │ @SquadPlan     │
  └────────┬───────┘  └────────────────┘   └───────┬────────┘
           │                                        │
           └──────────────────┬─────────────────────┘
                              │
           ┌──────────────────┼──────────────────────┐
           ▼                  ▼                       ▼
  ┌────────────────┐  ┌────────────────┐   ┌────────────────┐
  │  ReplyAgent    │  │  TicketAgent   │   │ComplianceAgent │
  │   SUPPORT      │  │   SUPPORT      │   │   CRITIC       │
  │ @SquadPlan     │  │ @SquadPlan     │   │ @SquadPlan     │
  │ @Improve       │  │                │   │                │
  └────────────────┘  └────────────────┘   └────────────────┘
```

### Agent Specifications

#### `MonitorAgent` — STRATEGIST

**Responsibility:** Pipeline orchestration and event routing  
**Annotations:** `@OnEvent`, `@Delegate`, `@OnMessage`, `@PostConstruct`

```
LLM prompt theme: "You are the orchestration agent. Triage incoming mentions:
route high-urgency to ANALYST immediately, batch low-urgency for efficiency."
```

**Key behaviour:**  
- Subscribes to `"mention.incoming"` topic via `@OnEvent`
- Uses `@Delegate` with `FIRST_MATCH` strategy to route to
  `ANALYST` (urgent/fraud/viral) or `RESEARCHER` (analytics/trend/batch)
- `@OnMessage` listens for analysis-complete signals from `ANALYST`

---

#### `SentimentAgent` — ANALYST

**Responsibility:** Multi-dimensional sentiment classification  
**Annotations:** `@SquadPlan`, `@Required`, `@PostConstruct`

**Output: `SentimentAnalysis`**
```java
{
  sentiment         // POSITIVE | NEGATIVE | NEUTRAL
  score             // "0.0" – "1.0" confidence
  primaryEmotion    // FRUSTRATION | ANGER | SADNESS | JOY | SARCASM | ...
  urgency           // LOW | MEDIUM | HIGH | CRITICAL
  topic             // PAYMENT_FAILURE | APP_CRASH | KYC_ISSUE | FRAUD_REPORT | ...
  summary           // one-line AI summary (max 100 chars)
  keywords          // 3-5 key terms
  requiresHumanReview // true if ambiguous or high-stakes
  suggestedTeam     // TECH_SUPPORT | BILLING | ESCALATIONS | CUSTOMER_CARE
}
```

**Prompt engineering decisions:**
- Explicitly asks for sarcasm detection (common in Indian social media)
- Lists all valid enum values in the prompt to prevent hallucination
- `CRITICAL` trigger list: fraud reports, regulatory body mentions, viral content (>50 RTs)

---

#### `EscalationAgent` — CRITIC

**Responsibility:** Priority scoring and SLA assignment  
**Annotations:** `@SquadPlan`, `@Required`, `@PostConstruct`

**Output: `EscalationDecision`**
```java
{
  priority                // P1 | P2 | P3 | P4
  escalationPath          // TECH_SUPPORT | BILLING | FRAUD_TEAM | LEGAL | EXECUTIVE
  slaHours                // P1=1h | P2=4h | P3=24h | P4=72h
  isViralRisk             // true if >10K follower author or mention going viral
  requiresImmediateAction // true for fraud, regulatory, legal threats
  reason                  // explanation
  notifyTeams             // list of teams to notify
}
```

**P1 triggers (hardcoded in prompt):**
- Fraud allegations with `@RBI_Informs`, `@finmin`, or regulatory body tags
- Viral content with >50 retweets
- Account blocked with implied large balance
- Author is a verified media journalist

**Priority normalisation:**  
LLMs frequently return `"CRITICAL"`, `"High"`, `"Highest"` instead of `P1/P2/P3/P4`.
The `normalizePriority()` method in `MentionProcessingService` maps all variants:

```java
"CRITICAL" / "URGENT"  → "P1"
"HIGH"                 → "P2"
"MEDIUM" / "NORMAL"    → "P3"
"LOW"                  → "P4"
```

---

#### `ReplyAgent` — SUPPORT

**Responsibility:** Brand-safe reply generation  
**Annotations:** `@SquadPlan`, `@Required`, `@Improve`, `@PostConstruct`

**Output: `GeneratedReply`**
```java
{
  replyText                // max 280 characters (Twitter limit)
  replyTone                // APOLOGETIC | THANKFUL | HELPFUL | URGENT
  includesCallToAction     // bool
  estimatedEngagementScore // 0-10
  alternativeReply         // fallback option
}
```

**Reply rules enforced in prompt:**
1. Acknowledge + apologise for NEGATIVE mentions — never deflect
2. Do NOT make promises that cannot be kept
3. Include `@AirtelPaymentsBank` hashtag where appropriate
4. For P1/fraud: provide hotline reference, escalate tone
5. Never reveal internal systems, ticket IDs, or employee names
6. Call-to-action: DM for sensitive issues

**`@Improve` integration:**  
When human approves or rejects a reply, a `FeedbackExample` is saved.
On subsequent calls, `@Improve` retrieves the top-5 most relevant
approved/rejected examples and injects them into the prompt as
few-shot examples. Over time, replies improve without retraining.

---

#### `ComplianceAgent` — CRITIC

**Responsibility:** Brand voice and regulatory compliance review  
**Annotations:** `@SquadPlan`, `@Required`, `@PostConstruct`

**Output: `ComplianceReview`**
```java
{
  approved                // true | false
  brandVoiceCompliant     // matches professional, empathetic, solution-focused
  regulatoryCompliant     // no financial advice, no data disclosure, RBI/SEBI safe
  noPromises              // no unkept promises
  noPersonalDataExposure  // no account numbers, KYC data in reply
  suggestions             // list of improvements if not approved
  revisedReply            // corrected reply if suggestions exist
}
```

**Every AI-generated reply passes through this agent before entering the
approval queue.** If `approved=false`, the revised reply replaces the original.

---

#### `TicketAgent` — SUPPORT

**Responsibility:** CRM ticket payload generation  
**Annotations:** `@SquadPlan`, `@Required`, `@PostConstruct`

**Output: `TicketPayload`**
```java
{
  title                  // concise issue title (max 80 chars)
  description            // full context: tweet + sentiment + urgency + URL
  category               // maps to internal support taxonomy
  priority               // P1 | P2 | P3 | P4
  tags                   // list for routing
  suggestedResolution    // initial troubleshooting steps
  customerContact        // DM / phone / email preference
  internalNotes          // context for agent — not visible to customer
  estimatedResolutionHours
}
```

**Design principle:** "The ticket should have enough context for any support
agent to resolve the issue without reading the original tweet."

---

#### `TrendAgent` — RESEARCHER

**Responsibility:** Pattern detection and brand health scoring  
**Annotations:** `@SquadPlan`, `@PostConstruct`

Used for batch analytics calls, not in the per-mention pipeline.
Analyses groups of mentions to detect: emerging topic clusters,
sentiment drift, spike patterns, viral risk index, brand health score (0-100).

---

### Agent Configuration

Each agent is configured at the LLM level via `LlmOptions`:

```java
// Role defaults (squad-core built-in)
STRATEGIST: temperature=0.2, maxTokens=1024  // deterministic routing
ANALYST:    temperature=0.1, maxTokens=2048  // precise classification
RESEARCHER: temperature=0.5, maxTokens=2048  // creative analysis
SUPPORT:    temperature=0.7, maxTokens=1024  // natural language generation
CRITIC:     temperature=0.1, maxTokens=1024  // strict evaluation
```

Application-level overrides via `squad.yml` or `application.properties`.

---

## 8. Data Architecture

### Entity Model

```
┌───────────────────────────────────────────────────────────────────────┐
│                           mentions                                    │
│                                                                       │
│  id               VARCHAR(50)   PK                                   │
│  platform         VARCHAR(50)   TWITTER | LINKEDIN | INSTAGRAM        │
│  handle           VARCHAR(100)  @AirtelPaymentsBank                  │
│  tenant_id        VARCHAR(36)   multi-tenant support                  │
│  author_username  VARCHAR(100)                                        │
│  author_name      VARCHAR(255)                                        │
│  author_followers BIGINT                                              │
│  text             TEXT          original mention content              │
│  language         VARCHAR(10)   en | hi | ta | ...                   │
│  posted_at        TIMESTAMP                                           │
│  ingested_at      TIMESTAMP                                           │
│  url              VARCHAR(1000) link to original post                 │
│  like_count       BIGINT                                              │
│  retweet_count    BIGINT                                              │
│                                                                       │
│  -- AI Analysis Results                                              │
│  sentiment_label  VARCHAR(20)   POSITIVE | NEGATIVE | NEUTRAL        │
│  sentiment_score  DOUBLE        0.0 – 1.0                            │
│  primary_emotion  VARCHAR(50)   FRUSTRATION | JOY | ANGER | ...     │
│  urgency          VARCHAR(20)   LOW | MEDIUM | HIGH | CRITICAL       │
│  topic            VARCHAR(100)  PAYMENT_FAILURE | APP_CRASH | ...   │
│  summary          VARCHAR(500)  one-line AI summary                  │
│  priority         VARCHAR(20)   P1 | P2 | P3 | P4                   │
│  escalation_path  VARCHAR(100)  TECH_SUPPORT | BILLING | ...        │
│  assigned_team    VARCHAR(100)                                        │
│                                                                       │
│  -- Reply Management                                                  │
│  reply_text       TEXT          AI-generated reply                    │
│  reply_status     VARCHAR(30)   PENDING | APPROVED | REJECTED | POSTED│
│                                                                       │
│  -- Ticket Management                                                 │
│  ticket_id        VARCHAR(100)  CRM ticket ID                        │
│  ticket_system    VARCHAR(50)   ZENDESK | JIRA | MOCK                │
│  ticket_status    VARCHAR(50)   OPEN | IN_PROGRESS | RESOLVED        │
│                                                                       │
│  -- Metadata                                                          │
│  processing_status VARCHAR(30)  NEW | ANALYSING | DONE | ERROR       │
│  urgency_score    INTEGER       0-100                                 │
│  viral_risk_score INTEGER       0-100                                 │
│  is_viral         BOOLEAN                                             │
│  created_at       TIMESTAMP                                           │
│  updated_at       TIMESTAMP                                           │
└───────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                            users                                │
│  id            VARCHAR(36)   PK (UUID)                         │
│  username      VARCHAR(100)  UNIQUE                             │
│  email         VARCHAR(255)  UNIQUE                             │
│  password_hash VARCHAR(255)  BCrypt(12)                         │
│  full_name     VARCHAR(255)                                     │
│  role          VARCHAR(50)   ADMIN | ANALYST | REVIEWER | READ_ONLY │
│  tenant_id     VARCHAR(36)                                      │
│  active        BOOLEAN                                          │
│  created_at    TIMESTAMP                                        │
│  updated_at    TIMESTAMP                                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        tenant_config                            │
│  tenant_id        VARCHAR(36)  PK                               │
│  brand_name       VARCHAR(255)                                  │
│  handle           VARCHAR(100) @AirtelPaymentsBank              │
│  platform         VARCHAR(50)                                   │
│  brand_tone       VARCHAR(500) professional,empathetic,...      │
│  ticket_system    VARCHAR(50)  MOCK | ZENDESK | JIRA            │
│  ticket_api_url   VARCHAR(500)                                  │
│  ticket_api_key   VARCHAR(500) (encrypted in prod)              │
│  auto_reply       BOOLEAN                                       │
│  require_approval BOOLEAN                                       │
│  active           BOOLEAN                                       │
│  created_at       TIMESTAMP                                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      refresh_tokens                             │
│  id            VARCHAR(36)   PK                                 │
│  user_id       VARCHAR(36)   FK → users.id                     │
│  token         VARCHAR(512)  UNIQUE                             │
│  expires_at    TIMESTAMP                                        │
│  created_at    TIMESTAMP                                        │
└─────────────────────────────────────────────────────────────────┘
```

### Database Indexes

```sql
-- Query patterns optimised
idx_mentions_handle         ON mentions(handle)
idx_mentions_sentiment      ON mentions(sentiment_label)
idx_mentions_posted_at      ON mentions(posted_at DESC)   -- time-range queries
idx_mentions_priority       ON mentions(priority)
idx_mentions_reply_status   ON mentions(reply_status)
idx_mentions_processing     ON mentions(processing_status)
idx_mentions_tenant         ON mentions(tenant_id)        -- multi-tenant
```

### Flyway Migration Strategy

```
db/sentinel/
├── V100__initial_schema.sql     ← mentions + users + refresh_tokens + indexes
├── V200__seed_admin_user.sql    ← default admin (via DataInitializer, not SQL hash)
└── V300__tenant_config.sql      ← tenant_config table + default Airtel tenant
```

**Namespace:** `db/sentinel/` (not `db/migration/`) to avoid conflict with
`squad-core`'s bundled `V1__squados_memory.sql` migration.

**H2 compatibility:** `ON CONFLICT` (PostgreSQL-only) replaced with `MERGE INTO ... KEY(col)`
which runs on both H2 (dev) and PostgreSQL (prod).

---

## 9. API Design

### REST Endpoints

**Base URL:** `http://localhost:8090`  
**Authentication:** `Authorization: Bearer {JWT}` (except `/api/auth/login`, `/api/auth/register`)

#### Authentication

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/login` | Public | Authenticate — returns JWT |
| `POST` | `/api/auth/register` | Public | Self-register (REVIEWER role) |
| `GET` | `/api/auth/me` | Authenticated | Current user profile |

**Login request/response:**
```json
// POST /api/auth/login
{ "username": "admin", "password": "Admin@123" }

// 200 OK
{
  "token":     "eyJhbGciOiJIUzM4NJ9...",
  "username":  "admin",
  "email":     "admin@sentinel.ai",
  "role":      "ADMIN",
  "tenantId":  "default",
  "expiresAt": "2026-03-25T03:35:31Z"
}
```

#### Mentions

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/api/mentions` | Authenticated | List mentions (filters: sentiment, status, priority) |
| `GET` | `/api/mentions/{id}` | Authenticated | Single mention with full analysis |
| `POST` | `/api/mentions/ingest` | ANALYST+ | Submit a mention for AI processing |
| `POST` | `/api/mentions/{id}/reply/approve` | REVIEWER+ | Approve AI reply |
| `POST` | `/api/mentions/{id}/reply/reject` | REVIEWER+ | Reject with optional revised text |
| `GET` | `/api/pending-replies` | REVIEWER+ | All mentions awaiting reply approval |
| `GET` | `/api/alerts` | Authenticated | P1 mentions from last 1 hour |

**Ingest request:**
```json
POST /api/mentions/ingest
{
  "text":      "@AirtelPaymentsBank UPI failed Rs5000 deducted!",
  "author":    "angry_user99",
  "followers": 12500
}
// 200 OK: { "status": "ingested", "message": "Processing in background" }
```

**Processing is async** — the HTTP call returns immediately; the AI pipeline
runs in a virtual thread and pushes updates via WebSocket.

#### Analytics

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/api/analytics/summary?hours=24` | Authenticated | KPI summary |
| `GET` | `/api/analytics/trend?hours=24` | Authenticated | Hourly sentiment trend |
| `GET` | `/api/analytics/categories?hours=24` | Authenticated | Category breakdown |
| `GET` | `/api/analytics/health` | Authenticated | Brand health score |

**Summary response:**
```json
{
  "totalMentions":    47,
  "positiveMentions": 12,
  "negativeMentions": 28,
  "neutralMentions":  7,
  "brandHealthScore": 31.4,
  "criticalAlerts":   3,
  "pendingReplies":   9,
  "openTickets":      5,
  "resolvedTickets":  2,
  "avgSentimentScore": 0.41
}
```

#### Tickets

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/api/tickets` | ANALYST+ | All CRM tickets |
| `POST` | `/api/tickets/{id}/resolve` | ANALYST+ | Mark ticket resolved |

#### Admin

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `GET` | `/api/admin/users` | ADMIN | List all users |
| `PUT` | `/api/admin/users/{id}/role` | ADMIN | Change user role |
| `PUT` | `/api/admin/users/{id}/toggle` | ADMIN | Enable/disable user |
| `PUT` | `/api/admin/users/{username}/promote` | ADMIN | Promote to ADMIN |

### WebSocket Events

**Endpoint:** `ws://localhost:8090/ws/mentions`  
**Protocol:** Text frames, JSON

```json
// mention.new — immediately on ingestion
{ "type": "mention.new", "data": { "id": "MOCK-abc123", "text": "...", "processingStatus": "NEW" }}

// mention.processed — after AI pipeline completes (5-30 seconds later)
{ "type": "mention.processed", "data": { "id": "MOCK-abc123", "sentimentLabel": "NEGATIVE",
  "priority": "P1", "replyText": "...", "replyStatus": "PENDING", ... }}

// mention.error — if pipeline fails
{ "type": "mention.error", "data": { "id": "MOCK-abc123", "processingStatus": "ERROR" }}
```

**Frontend handling:** `useLiveEvents()` hook maintains the connection,
auto-reconnects every 3 seconds on disconnect, and merges events into the
polled mention list without replacing historical data.

---

## 10. Security Architecture

### Authentication Flow

```
User                 Frontend            Backend             Database
 │                      │                   │                   │
 │ Enter credentials     │                   │                   │
 │──────────────────────►│                   │                   │
 │                      │ POST /api/auth/login│                  │
 │                      │──────────────────►│                   │
 │                      │                   │ findByUsername()  │
 │                      │                   │──────────────────►│
 │                      │                   │ BCrypt.verify()   │
 │                      │                   │ JwtService.generate()
 │                      │ 200 { token, role }│                   │
 │                      │◄──────────────────│                   │
 │                      │ localStorage.set() │                   │
 │ Dashboard loads      │                   │                   │
 │◄──────────────────────│                   │                   │
```

### JWT Token Structure

**Algorithm:** HS256 (minimum 256-bit secret)  
**Expiry:** 24 hours  
**Claims:**

```json
{
  "sub":      "admin",
  "role":     "ADMIN",
  "tenantId": "default",
  "userId":   "bfdbf218-5685-4797-b8b7-4d00326b1618",
  "iat":      1774323331,
  "exp":      1774409731
}
```

### Role-Based Access Control

| Role | Permissions |
|------|------------|
| `ADMIN` | All endpoints including user management |
| `ANALYST` | Read + analytics + ingest + ticket management |
| `REVIEWER` | Read + reply approve/reject |
| `READ_ONLY` | GET analytics + mentions only |

**Endpoint protection matrix:**

```
Public:        POST /api/auth/login  POST /api/auth/register
               GET /ws/**  GET /h2-console/**  GET /actuator/health

Authenticated: GET /api/mentions  GET /api/analytics/**  GET /api/alerts

REVIEWER+:     POST /api/mentions/*/reply/**  GET /api/pending-replies

ANALYST+:      POST /api/mentions/ingest  GET/POST /api/tickets/**

ADMIN only:    GET/PUT /api/admin/**
```

**Session management:**  
- Stateless (no server-side sessions)
- `JwtAuthFilter` extracts + validates token on every request
- Frontend auto-redirects to login on 401 response

### Password Security

- BCrypt with cost factor 12
- Admin user created programmatically by `DataInitializer` using `BCryptPasswordEncoder`
  (never hardcoded hash in SQL migrations — hashes are implementation-specific)
- Default credentials: `admin / Admin@123` — must be changed before production

---

## 11. Integration Architecture

### LLM Integration

```
                    LlmPort (interface)
                         │
         ┌───────────────┼────────────────────┐
         ▼               ▼                    ▼
SpringAiLlmAdapter   TokenTrackingPort    Custom adapters
 (via Ollama)        (wraps any LlmPort)  (OpenAI, Anthropic)
         │
         ▼
 ChatClient (Spring AI)
         │
         ▼
 Ollama API (:11434)  ── llama3.2
```

**Token tracking:**  
`TokenTrackingLlmPort` wraps `SpringAiLlmAdapter`. When Ollama returns 0
tokens (common with local models), it estimates using `content.length / 4`
chars-per-token heuristic.

**Model selection:** All 7 agents use the same model configured in
`spring.ai.ollama.chat.options.model`. Per-agent model overrides are
possible via `squad.yml` agent configuration.

### Twitter/X API v2 Integration

```
TwitterMentionIngestionService
  │
  ├── Mode 1: Filtered Stream (real-time)
  │     POST https://api.twitter.com/2/tweets/search/stream/rules
  │     GET  https://api.twitter.com/2/tweets/search/stream
  │     └── Server-Sent Events, virtual thread, auto-reconnect
  │
  └── Mode 2: Recent Search (polling fallback)
        GET https://api.twitter.com/2/tweets/search/recent
        └── @Scheduled every 5 minutes
```

**Stream rule:** `@AirtelPaymentsBank OR #AirtelPaymentsBank`  
**Tweet fields:** `created_at, author_id, public_metrics, lang`  
**Expansions:** `author_id → user.fields: name, username, public_metrics`

**Configuration:**
```properties
sentinel.twitter.bearer-token=     # set in prod env
sentinel.twitter.enabled=false     # must opt-in
sentinel.twitter.stream-enabled=true
sentinel.twitter.poll-interval-ms=300000
```

### CRM Integration

```
TicketConnector (interface)
     │
     ├── ZendeskConnector
     │     POST https://{subdomain}.zendesk.com/api/v2/tickets.json
     │     Authentication: Basic {base64(email:token)}
     │     Priority mapping: P1→urgent, P2→high, P3→normal, P4→low
     │
     ├── JiraConnector
     │     POST https://{url}/rest/api/3/issue
     │     Authentication: Basic {base64(email:token)}
     │     Description format: Atlassian Document Format (ADF)
     │     Priority mapping: P1→Highest, P2→High, P3→Medium, P4→Low
     │
     └── MockTicketConnector  (default when none enabled)
           In-memory, sequential IDs (TKT-1001, TKT-1002, ...)
```

**TicketConnectorFactory** selects the first enabled connector:
```
ZendeskConnector.isEnabled() = sentinel.zendesk.enabled=true AND subdomain≠""
JiraConnector.isEnabled()    = sentinel.jira.enabled=true AND url≠""
Default                      = MockTicketConnector
```

---

## 12. Processing Pipeline

### Per-Mention Pipeline (5 Steps)

```
STEP 1: INGESTION
  TwitterMentionIngestionService (real) or MockMentionIngestionService (dev)
  └── Creates MentionEntity { processingStatus="NEW" }
  └── Saves to DB
  └── WebSocket broadcast: "mention.new"
  └── Spawns virtual thread

STEP 2: SENTIMENT ANALYSIS
  SentimentAgent (ANALYST role)
  └── Input: platform, author, text, likes, retweets
  └── Output: SentimentAnalysis { sentiment, score, emotion, urgency, topic, summary, team }
  └── Applied to MentionEntity fields

STEP 3: ESCALATION SCORING
  EscalationAgent (CRITIC role)
  └── Input: mention context + sentiment + urgency + followers
  └── Output: EscalationDecision { priority, escalationPath, slaHours, isViralRisk }
  └── normalizePriority() ensures P1/P2/P3/P4 format
  └── Applied to MentionEntity priority + assignedTeam

STEP 4: REPLY GENERATION (if sentinel.auto-reply.enabled=true)
  4a. ReplyAgent (SUPPORT role)
      └── Input: mention + sentiment + priority + brand name
      └── Output: GeneratedReply { replyText, replyTone, callToAction }
  4b. ComplianceAgent (CRITIC role)
      └── Input: original mention + proposed reply
      └── Output: ComplianceReview { approved, suggestions, revisedReply }
      └── If not approved, revisedReply replaces original
  └── mention.replyText = final reply
  └── mention.replyStatus = "PENDING" (awaits human approval)

STEP 5: TICKET CREATION (if NEGATIVE OR P1 OR P2)
  TicketAgent (SUPPORT role)
  └── Input: full mention context + sentiment + priority + category
  └── Output: TicketPayload { title, description, priority, tags, resolution, notes }
  └── TicketConnectorFactory.get().createTicket(mention, payload)
  └── mention.ticketId = returned ID
  └── mention.ticketStatus = "OPEN"

STEP 6: COMPLETION
  └── mention.processingStatus = "DONE"
  └── Saves to DB
  └── WebSocket broadcast: "mention.processed"
  └── FeedbackStore.save(FeedbackExample for @Improve)
  └── SquadTracer.export(AgentSpan)
```

**Error handling:**  
Any exception in steps 2-5 marks `processingStatus = "ERROR"` and broadcasts
`"mention.error"`. The mention is never silently dropped. The error is logged
and the dashboard alert tab captures it.

### Analytics Pipeline (On-Demand)

```
GET /api/analytics/summary?hours=24
  └── AnalyticsService.getSummary(24)
       └── repo.findByPostedAtAfterOrderByPostedAtDesc(since)
       └── Computes:
            - totalMentions, positive, negative, neutral
            - brandHealthScore = 50 + (positive - negative×2) / total × 50
            - criticalAlerts (P1 count)
            - pendingReplies (replyStatus="PENDING")
            - openTickets / resolvedTickets
            - avgSentimentScore
```

**Brand Health Score formula:**
```
score = clamp(0, 100, 50 + (positive - negative×2) / max(total, 1) × 50)
```
- All positive, no negative → 100
- Equal positive and negative → 50
- Heavy negative → approaches 0

---

## 13. Observability and Monitoring

### Trace Data (SquadOS `@Traced`)

Every agent call produces an `AgentSpan`:

```java
AgentSpan {
  spanId          // UUID
  traceId         // UUID
  spanName        // e.g., "mention-processing"
  agentName       // e.g., "MonitorAgent"
  agentRole       // STRATEGIST | ANALYST | ...
  durationMs      // wall-clock time
  status          // OK | ERROR
  promptTokens    // LLM input tokens
  completionTokens// LLM output tokens
  totalTokens     // prompt + completion
  inputLength     // prompt character count
  outputLength    // response character count
  errorMessage    // set if status = ERROR
  startTime       // Instant
}
```

**Exporters:**
- Dev: `InMemoryTraceExporter` — in-process, max 500 spans
- Prod: `RedisTraceExporter` → `squados:traces` (LIST), `squados:traces:tokens` (STRING)

### Structured Logging

Key log patterns to monitor in production:

```
[MonitorAgent]       Social media monitoring pipeline online.
[SentimentAgent]     Multi-dimensional sentiment engine online.
[Twitter]            Stream rule set: 201
[Twitter]            @angry_user: @AirtelPaymentsBank UPI failed...
[MentionService]     Error processing MOCK-abc123: ...     ← alert on this
[MockTicket]         Created: TKT-1001 — Payment failure issue
[DataInitializer]    Admin user created — CHANGE PASSWORD IN PRODUCTION
```

### Health Check

`GET /actuator/health` — publicly accessible, returns:
```json
{ "status": "UP", "components": { "db": { "status": "UP" } } }
```

---

## 14. Deployment Architecture

### Dev Environment

```bash
# Terminal 1: LLM
ollama run llama3.2

# Terminal 2: Backend (H2 in-memory, mock data every 30s)
cd sentinel-backend && mvn spring-boot:run -Dspring.profiles.active=dev

# Terminal 3: Frontend (Vite dev server + proxy)
cd sentinel-frontend && npm run dev

# Access: http://localhost:3000  (frontend)
#         http://localhost:8090  (backend API)
#         http://localhost:8090/h2-console (database)
```

### Docker Compose (Staging/Prod)

```yaml
services:
  redis:     redis:7-alpine             :6379
  ollama:    ollama/ollama:latest       :11434
  backend:   sentinel-backend image     :8090
  frontend:  nginx serving React dist   :3000 → Nginx → backend

  # Prod only (not in docker-compose, managed externally)
  postgres:  PostgreSQL 16              :5432
```

**Backend environment variables (prod):**
```bash
DB_URL=jdbc:postgresql://postgres:5432/sentineldb
DB_USER=sentinel
DB_PASS=${SECRET_DB_PASS}
SPRING_PROFILES_ACTIVE=prod
SENTINEL_TWITTER_BEARER_TOKEN=${SECRET_TWITTER_TOKEN}
SENTINEL_ZENDESK_API_TOKEN=${SECRET_ZENDESK_TOKEN}
SENTINEL_JWT_SECRET=${SECRET_JWT_KEY}    # min 256 bits
REDIS_HOST=redis
REDIS_PORT=6379
```

### Production Architecture (Target)

```
Internet
  │
  ▼
[CDN / WAF]  ← CloudFront, Cloudflare, or equivalent
  │
  ▼
[Load Balancer]  ← ALB/NLB
  │
  ├── :443  → [Frontend Nodes]  ← Nginx serving static React build
  │                 │
  │                 │  /api/* → Backend
  │                 │  /ws/*  → Backend (WebSocket upgrade)
  │
  └── :8090 → [Backend Nodes] ← Spring Boot (multiple instances)
                    │
                    ├── [PostgreSQL]   ← RDS / Cloud SQL
                    ├── [Redis]        ← ElastiCache / Memorystore
                    └── [Ollama]       ← GPU instance or managed LLM API
```

---

## 15. Scalability and Performance

### Current Limits

| Bottleneck | Limit | Mitigation |
|-----------|-------|-----------|
| LLM latency | 5-30s per mention | Virtual threads — 10 concurrent |
| Ollama (local) | ~2 concurrent requests | Use managed API for prod |
| H2 database | Single JVM, in-memory | Switch to PostgreSQL for prod |
| In-process event bus | Single JVM | Kafka adapter for multi-instance |
| WebSocket | 1000s of clients | Sticky sessions at load balancer |

### Scaling Strategy

**Phase 1 — Vertical (current):**  
Single backend instance, Ollama local, H2 → PostgreSQL

**Phase 2 — Managed LLM:**  
Replace Ollama with OpenAI GPT-4o or Anthropic Claude — eliminates the GPU
bottleneck, improves concurrency to 100+ concurrent mentions

**Phase 3 — Horizontal:**  
- Replace `InProcessEventBus` with Kafka (mention.raw, mention.analysed)
- Replace `InProcessApprovalStore` with database-backed store
- Multiple backend instances sharing PostgreSQL + Redis
- Redis for WebSocket pub/sub across instances

**Phase 4 — Multi-region:**  
- Each region has its own agent cluster
- Shared PostgreSQL (Aurora Global) + Redis (Elasticache Global)
- Content delivery for frontend via CDN

### Performance Targets (Phase 2)

| Metric | Target |
|--------|--------|
| Mention throughput | 100/minute |
| LLM latency (P95) | < 5s (managed API) |
| API response time (P99) | < 200ms |
| Dashboard users (concurrent) | 50+ |
| Mentions stored | 10M+ (PostgreSQL) |

---

## 16. Technology Decisions

### Why SquadOS?

SquadOS provides exactly what this use case needs out of the box:
- `@SquadPlan` typed structured outputs — every agent returns a typed object
- `@Improve` few-shot learning — reply quality improves over time automatically
- `@OnEvent` event-driven architecture — ready for Kafka migration
- `@Traced` observability — span + token tracking for cost monitoring
- `LlmPort` abstraction — switch from Ollama to OpenAI in one line

Building equivalent functionality on LangChain4j or raw Spring AI would
require 3-5× more code and no built-in agent coordination primitives.

### Why Spring Boot 3.3?

- Native Spring AI integration (`ChatClient`, structured output, model options)
- Spring Data JPA + Hibernate for PostgreSQL/H2
- Spring Security with stateless JWT filter chain
- Virtual threads (Java 21) via `Thread.ofVirtual()` — no reactive complexity
- Spring WebSocket for real-time push

### Why React 18 + Vite?

- TypeScript type safety across all API contracts
- Recharts for server-rendered charts (SSR-compatible, lightweight)
- Vite for sub-second hot module replacement during development
- `useCallback` + `useRef` prevent unnecessary re-renders in live feed

### Why H2 for dev?

- Zero setup — schema created by Flyway on first run
- `MODE=PostgreSQL` ensures SQL compatibility
- `h2-console` at `/h2-console` for ad-hoc inspection
- Switch to PostgreSQL is a single property change

### Why OkHttp for Twitter and CRM connectors?

- Minimal dependency (no Spring Web client needed for external REST calls)
- Supports SSE (Server-Sent Events) streaming for Twitter filtered stream
- `readTimeout(0)` for indefinite streaming connection
- Used for both Twitter API v2 and Zendesk/Jira HTTP calls

### Why JWT over sessions?

- Stateless — any backend instance can validate without shared state
- Enables horizontal scaling without sticky sessions
- Standard approach for React SPA + REST API architectures
- Payload contains role + tenantId — avoids DB lookup per request

---

## 17. Risk Register

| ID | Risk | Probability | Impact | Mitigation |
|----|------|------------|--------|-----------|
| R1 | LLM returns malformed JSON (no valid @SquadPlan output) | HIGH | MEDIUM | SquadOS retries up to 3× with `@Required` validation; fallback to ERROR status |
| R2 | LLM returns "CRITICAL" instead of "P1" for priority | HIGH | MEDIUM | `normalizePriority()` maps all variants; currently handled in service layer |
| R3 | Twitter API rate limit hit | MEDIUM | HIGH | Exponential backoff; Recent Search as stream fallback; `@Scheduled` polling |
| R4 | Ollama GPU memory overflow with 10 concurrent agents | MEDIUM | HIGH | Reduce `concurrency` in `@OnEvent`; move to managed LLM API for prod |
| R5 | BCrypt hash mismatch between environments | LOW | HIGH | Admin user created by `DataInitializer` at runtime — not hardcoded in SQL |
| R6 | Flyway migration conflict with squad-core's V1 | RESOLVED | HIGH | SentinelAI migrations in `db/sentinel/` namespace starting at V100 |
| R7 | WebSocket connection lost during long LLM processing | MEDIUM | LOW | Auto-reconnect every 3s; mentions polled every 10s as fallback |
| R8 | Compliance agent approves harmful reply | LOW | CRITICAL | Human approval queue is mandatory — no reply posts without human sign-off |
| R9 | SQL column too short for LLM output | OCCURRED | MEDIUM | `priority VARCHAR(5→20)` + `normalizePriority()` fix deployed |
| R10 | Multi-tenant data leakage | LOW | CRITICAL | `tenant_id` column on mentions + users; API filters by tenantId from JWT |

---

## 18. Roadmap

### Tier 1 — Production Readiness ✅ (Complete)

- [x] PostgreSQL + Flyway migrations
- [x] Spring Security + JWT authentication
- [x] Twitter API v2 (filtered stream + polling)
- [x] Zendesk + Jira CRM connectors
- [x] Frontend auth (login page + JWT headers)

### Tier 2 — Operational Excellence (Next)

- [ ] **Slack alerts** — P1 mentions pushed to `#incidents` channel in seconds
- [ ] **Multi-language sentiment** — Hindi, Tamil, Telugu (critical for India)
- [ ] **Reply auto-posting** — post approved replies via Twitter API
- [ ] **SLA countdown timers** — P1 = 1h, P2 = 4h, live dashboard countdowns
- [ ] **Kafka async processing** — decouple ingestion from analysis at scale
- [ ] **Category breakdown chart** — PAYMENT_FAILURE / APP_CRASH / BILLING

### Tier 3 — Advanced Intelligence

- [ ] **Viral prediction engine** — `@AutoPlan` monitors retweet velocity,
      predicts viral threshold, auto-escalates to PR team (crisis score 0-100)
- [ ] **Customer 360** — click `@username` → all their mentions + churn risk score
- [ ] **@SquadVote for ambiguous mentions** — 3 agents vote on priority
- [ ] **Reply A/B testing** — generate 2 variants, track engagement, feed back
      into `@Improve` store
- [ ] **Multi-platform** — LinkedIn, Instagram, App Store reviews, Reddit

### Tier 4 — Platform

- [ ] **Multi-tenant SaaS** — one deployment serves multiple brands
- [ ] **Settings page** — configure handle, brand tone, CRM, Twitter keys in UI
- [ ] **Mobile PWA** — installable, push notifications for P1 alerts
- [ ] **PDF/CSV reports** — daily/weekly brand health exports
- [ ] **Competitor monitoring** — track competitor mentions alongside own brand

---

## Appendix A: Configuration Reference

```properties
# ── Server ────────────────────────────────────────────────────────
server.port=8090
spring.profiles.active=dev          # dev | prod

# ── LLM ──────────────────────────────────────────────────────────
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.chat.options.temperature=0.3
spring.ai.ollama.chat.options.stream=false

# ── SquadOS ───────────────────────────────────────────────────────
squad.name=sentinel-squad
squad.llm.provider=ollama
squad.llm.model=llama3.2
squad.tracing.enabled=true

# ── Database (dev — H2) ───────────────────────────────────────────
spring.datasource.url=jdbc:h2:mem:sentineldb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.jpa.hibernate.ddl-auto=none
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/sentinel

# ── Database (prod — PostgreSQL) ──────────────────────────────────
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/sentineldb}
spring.datasource.username=${DB_USER:sentinel}
spring.datasource.password=${DB_PASS:changeme}

# ── JWT Security ──────────────────────────────────────────────────
sentinel.jwt.secret=<min-256-bit-random-secret>
sentinel.jwt.expiry-ms=86400000

# ── SentinelAI Config ─────────────────────────────────────────────
sentinel.handle=@AirtelPaymentsBank
sentinel.brand.name=Airtel Payments Bank
sentinel.brand.tone=professional,empathetic,solution-focused
sentinel.polling.interval-ms=30000
sentinel.mock.enabled=true           # false in prod
sentinel.auto-reply.enabled=true
sentinel.auto-reply.require-approval=true
sentinel.ticket.system=MOCK          # MOCK | ZENDESK | JIRA

# ── Twitter API v2 ───────────────────────────────────────────────
sentinel.twitter.bearer-token=${TWITTER_BEARER_TOKEN:}
sentinel.twitter.enabled=false       # true in prod
sentinel.twitter.stream-enabled=true
sentinel.twitter.poll-interval-ms=300000

# ── Zendesk ───────────────────────────────────────────────────────
sentinel.zendesk.subdomain=${ZENDESK_SUBDOMAIN:}
sentinel.zendesk.email=${ZENDESK_EMAIL:}
sentinel.zendesk.api-token=${ZENDESK_TOKEN:}
sentinel.zendesk.enabled=false

# ── Jira ─────────────────────────────────────────────────────────
sentinel.jira.url=${JIRA_URL:}
sentinel.jira.email=${JIRA_EMAIL:}
sentinel.jira.api-token=${JIRA_TOKEN:}
sentinel.jira.project-key=SENT
sentinel.jira.enabled=false

# ── Redis (for SquadOS shared tracing) ───────────────────────────
redis.host=localhost
redis.port=6379

# ── CORS ─────────────────────────────────────────────────────────
sentinel.cors.allowed-origins=http://localhost:3000,http://localhost:5173
```

---

## Appendix B: Glossary

| Term | Definition |
|------|-----------|
| **Squad** | A group of SquadOS AI agents working together on a shared goal |
| **Mention** | A social media post referencing the monitored brand |
| **Sentiment** | POSITIVE / NEGATIVE / NEUTRAL classification of a mention |
| **Urgency** | LOW / MEDIUM / HIGH / CRITICAL — how quickly response is needed |
| **Priority** | P1 (1h SLA) / P2 (4h) / P3 (24h) / P4 (72h) |
| **Viral risk** | Probability that a mention will gain significant amplification |
| **Brand Health Score** | 0-100 index computed from positive/negative ratio over 24h |
| **Reply Queue** | List of AI-generated replies awaiting human approval |
| **CRM connector** | Adapter that creates tickets in Zendesk, Jira, or Freshdesk |
| **LlmPort** | SquadOS interface abstraction over any LLM provider |
| **AgentSpan** | Observability record for a single agent LLM call |
| **Flyway** | Database migration tool — versioned SQL files |
| **JWT** | JSON Web Token — stateless authentication mechanism |
| **Tenant** | An organisation using SentinelAI (multi-tenancy ready) |
| **@SquadPlan** | SquadOS annotation that produces typed structured LLM output |
| **@Improve** | SquadOS annotation that injects few-shot examples from feedback |
| **Virtual thread** | Java 21 lightweight thread — enables high concurrency without reactive code |

---

*Document version: 1.0.0 · Architecture review: Required before Tier 2 deployment*  
*Next review date: June 2026*
