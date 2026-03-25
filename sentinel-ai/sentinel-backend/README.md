# SentinelAI Backend — Spring Boot + SquadOS + Multi-LLM

> Production-ready Spring Boot backend for enterprise social media monitoring with 7 SquadOS AI agents and support for 4 LLM providers.

## Quick Start

```bash
# 1. Prerequisites
ollama pull llama3.2          # or use OpenAI/Anthropic/Gemini
docker run -d -p 6379:6379 redis:7-alpine

# 2. Build & Run
mvn clean install -DskipTests
mvn spring-boot:run

# 3. Verify
curl http://localhost:8090/actuator/health
```

**API**: http://localhost:8090  
**WebSocket**: ws://localhost:8090/ws/mentions

## Architecture

```
┌─────────────────────────────────────────────┐
│           SentinelAI Backend                │
│        (Spring Boot 3.3 + SquadOS)          │
├─────────────────────────────────────────────┤
│  REST API (8090)  │  WebSocket (/ws/mentions)
├─────────────────────────────────────────────┤
│             SquadOS Context (7 Agents)      │
│  ┌──────────────────────────────────────┐  │
│  │ MonitorAgent (STRATEGIST)            │  │
│  │ ├─ SentimentAgent (ANALYST)          │  │
│  │ ├─ EscalationAgent (CRITIC)          │  │
│  │ ├─ ComplianceAgent (CRITIC)          │  │
│  │ ├─ ReplyAgent (SUPPORT)              │  │
│  │ ├─ TicketAgent (SUPPORT)             │  │
│  │ └─ TrendAgent (RESEARCHER)           │  │
│  └──────────────────────────────────────┘  │
├─────────────────────────────────────────────┤
│     LLM Provider (switchable at runtime)   │
│  Ollama │ OpenAI │ Anthropic │ Google     │
├─────────────────────────────────────────────┤
│      Social Media Ingestion Services       │
│  Twitter/X │ Facebook │ Instagram │ LinkedIn
├─────────────────────────────────────────────┤
│   Persistence Layer (H2 / PostgreSQL)      │
│   Cache Layer (Redis - optional)           │
└─────────────────────────────────────────────┘
```

## LLM Provider Configuration

### 1. Ollama (Default - Free, Local)

```bash
# Start Ollama
ollama serve

# Run backend (uses Ollama by default)
mvn spring-boot:run

# Or explicitly set provider
export LLM_PROVIDER=ollama
mvn spring-boot:run
```

**Pros**: Free, offline, no API keys needed  
**Cons**: Requires local setup, slower than cloud providers

### 2. OpenAI (GPT-4)

```bash
# Get API key: https://platform.openai.com/api-keys
export LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-your-key-here
mvn spring-boot:run
```

**Pros**: Best performance, state-of-the-art GPT-4  
**Cons**: $0.03-$0.06 per 1K tokens

### 3. Anthropic (Claude)

```bash
# Get API key: https://console.anthropic.com/
export LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=sk-ant-your-key-here
mvn spring-boot:run
```

**Pros**: Strong reasoning, good cost-performance ratio  
**Cons**: $0.003-$0.024 per 1K tokens

### 4. Google Gemini (Vertex AI)

```bash
# Setup Google Cloud & Vertex AI
export LLM_PROVIDER=gemini
export VERTEX_AI_PROJECT_ID=your-project-id
export VERTEX_AI_LOCATION=us-central1
mvn spring-boot:run
```

**Pros**: Affordable, multi-modal support  
**Cons**: Requires Google Cloud setup

## Social Media Integration

### Twitter/X API v2

```properties
# application.properties
sentinel.twitter.enabled=true
sentinel.twitter.bearer-token=${TWITTER_BEARER_TOKEN}
sentinel.twitter.stream-enabled=true   # real-time or false for polling
sentinel.twitter.poll-interval-ms=300000
```

**Features**:
- ✅ Filtered Stream API (real-time mentions)
- ✅ Search/Recent API (historical mentions - 7 days)
- ✅ Automatic rule management
- ✅ Backoff & retry logic

**Setup**: https://developer.twitter.com/en/portal/dashboard

### Facebook Graph API

```properties
sentinel.facebook.enabled=true
sentinel.facebook.access-token=${FACEBOOK_ACCESS_TOKEN}
sentinel.facebook.poll-interval-ms=300000
```

### Instagram Basic Display API

```properties
sentinel.instagram.enabled=true
sentinel.instagram.access-token=${INSTAGRAM_ACCESS_TOKEN}
```

### LinkedIn Marketing API

```properties
sentinel.linkedin.enabled=true
sentinel.linkedin.access-token=${LINKEDIN_ACCESS_TOKEN}
```

## Environment Variables

```bash
# LLM Provider
export LLM_PROVIDER=ollama                    # ollama, openai, anthropic, gemini
export OPENAI_API_KEY=sk-...                 # for OpenAI
export ANTHROPIC_API_KEY=sk-ant-...          # for Anthropic
export VERTEX_AI_PROJECT_ID=...              # for Google Gemini
export VERTEX_AI_LOCATION=us-central1        # for Google Gemini

# Social Media
export TWITTER_BEARER_TOKEN=AAAA...          # Twitter/X
export FACEBOOK_ACCESS_TOKEN=...             # Facebook
export INSTAGRAM_ACCESS_TOKEN=...            # Instagram
export LINKEDIN_ACCESS_TOKEN=...             # LinkedIn

# Database
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sentineldb
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=password

# Redis
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

## Project Structure

```
sentinel-backend/
├── src/main/java/io/sentinel/backend/
│   ├── SentinelApp.java                     # Spring Boot entry point
│   ├── adapter/
│   │   └── SpringAiLlmAdapter.java          # LLM bridge for SquadOS
│   ├── agents/
│   │   ├── MonitorAgent.java                # Event listener & router
│   │   ├── SentimentAgent.java              # Multi-dimensional sentiment
│   │   ├── EscalationAgent.java             # Priority scoring
│   │   ├── ComplianceAgent.java             # Brand compliance
│   │   ├── ReplyAgent.java                  # Auto-reply generation
│   │   ├── TicketAgent.java                 # CRM ticket creation
│   │   └── TrendAgent.java                  # Pattern & trend detection
│   ├── api/
│   │   ├── MentionController.java           # REST endpoints
│   │   ├── AnalyticsController.java         # Analytics endpoints
│   │   └── TicketController.java            # Ticket endpoints
│   ├── service/
│   │   ├── MentionProcessingService.java    # Orchestrator
│   │   └── TicketConnectorService.java      # CRM integration
│   ├── ingestion/
│   │   ├── TwitterMentionIngestionService.java    # Twitter streaming/polling
│   │   ├── FacebookMentionIngestionService.java   # Facebook Graph API
│   │   ├── InstagramMentionIngestionService.java  # Instagram API
│   │   ├── LinkedInMentionIngestionService.java   # LinkedIn API
│   │   └── MockMentionIngestionService.java       # Test data
│   ├── config/
│   │   ├── LlmConfiguration.java            # LLM provider routing
│   │   └── SecurityConfig.java              # JWT auth
│   └── websocket/
│       └── MentionWebSocketHandler.java     # Real-time updates
├── src/main/resources/
│   ├── application.properties                # Main config
│   ├── application-dev.properties            # Dev profile
│   ├── application-prod.properties           # Prod profile
│   └── db/sentinel/
│       ├── V100__initial_schema.sql
│       ├── V200__seed_admin_user.sql
│       └── V300__tenant_config.sql
└── pom.xml                                   # Maven dependencies
```

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.3.0 | Web framework |
| Spring AI | 1.0.0 | LLM abstraction (Ollama/OpenAI/Anthropic/Gemini) |
| SquadOS | 3.4.0 | AI agent orchestration |
| OkHttp3 | 4.12.0 | HTTP client (Twitter API) |
| Jackson | 2.17.1 | JSON serialization |
| Jedis | 5.1.0 | Redis client |
| H2 Database | 2.2.224 | In-memory DB (dev) |
| PostgreSQL | 42.7.3 | Production DB |
| Flyway | 10.10.0 | Database migrations |

## API Endpoints

### Mentions

```bash
# Get all mentions
GET /api/mentions?limit=20&offset=0&sentiment=POSITIVE

# Submit a mention for analysis
POST /api/mentions/ingest
{
  "text": "Love your service!",
  "platform": "TWITTER",
  "authorUsername": "user123",
  "handle": "@AirtelPaymentsBank"
}

# Approve AI-generated reply
POST /api/mentions/{id}/reply/approve
{ "replyText": "Thank you for the feedback!" }

# Reject reply
POST /api/mentions/{id}/reply/reject
{ "reason": "Tone not appropriate" }
```

### Analytics

```bash
# Brand health summary (24h)
GET /api/analytics/summary

# Sentiment trend (hourly)
GET /api/analytics/trend

# Brand health score
GET /api/analytics/health
```

### Tickets

```bash
# Get all tickets
GET /api/tickets?status=OPEN

# Resolve ticket
POST /api/tickets/{id}/resolve
{ "resolutionNote": "Customer issue resolved" }
```

### Alerts

```bash
# Get critical P1 mentions
GET /api/alerts?priority=P1
```

## WebSocket Events

```
Connection: ws://localhost:8090/ws/mentions

Events:
├─ { type: "mention.new",       data: Mention }
├─ { type: "mention.processed", data: Mention }
├─ { type: "sentiment.analyzed", data: Mention }
├─ { type: "ticket.created",    data: Ticket }
├─ { type: "reply.pending",     data: Mention }
└─ { type: "mention.error",     data: ErrorMessage }
```

## Configuration Files

### application.properties (Main)

Key settings for LLM, social media, and SentinelAI features.

```properties
# LLM Configuration
squad.llm.provider=${LLM_PROVIDER:ollama}
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.openai.chat.options.model=gpt-4

# Social Media
sentinel.twitter.enabled=false
sentinel.facebook.enabled=false
sentinel.instagram.enabled=false
sentinel.linkedin.enabled=false

# Features
sentinel.mock.enabled=true                    # Use mock data for testing
sentinel.auto-reply.enabled=true              # Generate replies
sentinel.auto-reply.require-approval=true     # Require human approval

# Brand Configuration
sentinel.brand.name=Airtel Payments Bank
sentinel.brand.tone=professional,empathetic,solution-focused
```

### application-prod.properties (Production)

Production overrides for security and performance.

```properties
spring.profiles.active=prod
spring.datasource.url=jdbc:postgresql://prod-db:5432/sentinel
spring.jpa.hibernate.ddl-auto=validate
logging.level.root=WARN
```

## Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=MentionProcessingServiceTest

# With coverage
mvn jacoco:report
```

## Troubleshooting

### Backend won't start

```bash
# Check if port 8090 is in use
lsof -i :8090

# Kill process on port 8090
kill -9 $(lsof -t -i:8090)
```

### Ollama connection errors

```bash
# Verify Ollama is running
curl http://localhost:11434/api/tags

# Or pull model
ollama pull llama3.2
```

### OpenAI/Anthropic API key not working

```bash
# Verify key format
echo $OPENAI_API_KEY      # should be sk-...
echo $ANTHROPIC_API_KEY   # should be sk-ant-...

# Check Spring logs
mvn spring-boot:run | grep -i "api.key"
```

### Redis connection issues

```bash
# Check Redis is running
redis-cli ping

# Or start Docker Redis
docker run -d -p 6379:6379 redis:7-alpine
```

## Deployment

### Docker

```bash
docker build -t sentinel-backend:latest .
docker run -d \
  -p 8090:8090 \
  -e LLM_PROVIDER=openai \
  -e OPENAI_API_KEY=$OPENAI_API_KEY \
  sentinel-backend:latest
```

### Kubernetes

```bash
kubectl apply -f sentinel-backend-deployment.yaml
kubectl port-forward svc/sentinel-backend 8090:8090
```

## Monitoring & Logging

### Enable Debug Logging

```bash
mvn spring-boot:run -Dspring.log.level=DEBUG
```

### Metrics

- Spring Boot Actuator: http://localhost:8090/actuator
- Health: http://localhost:8090/actuator/health
- Metrics: http://localhost:8090/actuator/metrics

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

[Apache 2.0](../../LICENSE)

## Support

- Issues: [GitHub Issues](https://github.com/sgpatel/squad-os/issues)
- Discussions: [GitHub Discussions](https://github.com/sgpatel/squad-os/discussions)
- Documentation: [Squad OS Docs](https://github.com/sgpatel/squad-os#readme)

