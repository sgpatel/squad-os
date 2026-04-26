# TutorOS - OpenAI Configuration

## Quick Start with OpenAI

### 1. Get OpenAI API Key

1. Sign up at https://platform.openai.com/
2. Navigate to API Keys section
3. Create a new API key
4. Copy the key (starts with `sk-proj-...`)

### 2. Set Environment Variable

```bash
export OPENAI_API_KEY="sk-proj-your-key-here"
```

Or add to your shell profile (`~/.zshrc` or `~/.bashrc`):

```bash
echo 'export OPENAI_API_KEY="sk-proj-your-key-here"' >> ~/.zshrc
source ~/.zshrc
```

### 3. Build and Run

```bash
cd /Users/deekshasingh/workspace/squad-os/examples/tutor-os
mvn clean install -DskipTests
mvn -pl tutor-api spring-boot:run
```

The application will start on http://localhost:8080

### 4. Verify OpenAI Connection

Check the startup logs for:

```
[SquadOS] LlmPort: Spring AI openai / gpt-4o (options applied per-agent)
```

---

## Model Selection

### Recommended Models

**For Production (Best Value - Recommended):**
```properties
spring.ai.openai.chat.options.model=gpt-4o-mini
```
- Latest GPT-4 mini model
- 82% cheaper than GPT-4o
- Excellent quality for most tasks
- ~$0.15 per 1M input tokens, ~$0.60 per 1M output tokens

**For Maximum Quality:**
```properties
spring.ai.openai.chat.options.model=gpt-4o
```
- Latest full GPT-4 model
- Best reasoning and accuracy
- ~$2.50 per 1M input tokens, ~$10 per 1M output tokens

**For Development (Faster, Cheaper):**
```properties
spring.ai.openai.chat.options.model=gpt-3.5-turbo
```
- Good for testing and development
- ~$0.50 per 1M input tokens, ~$1.50 per 1M output tokens

**For Complex Reasoning:**
```properties
spring.ai.openai.chat.options.model=gpt-4-turbo
```
- Previous generation GPT-4
- 128K context window

### Embedding Models

**For Semantic Memory (@AgentMemory):**
```properties
spring.ai.openai.embedding.options.model=text-embedding-3-small
```
- 1536 dimensions
- ~$0.02 per 1M tokens

**For Higher Accuracy:**
```properties
spring.ai.openai.embedding.options.model=text-embedding-3-large
```
- 3072 dimensions
- ~$0.13 per 1M tokens

---

## Cost Optimization

### 1. Enable Caching

```properties
# Cache LLM responses to reduce API calls
squad.cache.enabled=true
squad.cache.mode=SEMANTIC
squad.cache.ttl-seconds=3600
```

### 2. Rate Limiting

```properties
# Limit API usage
squad.rate-limit.tokens-per-hour=100000
squad.rate-limit.calls-per-minute=60
```

### 3. Per-Agent Model Selection

Use cheaper models for simple tasks in `squad.yml`:

```yaml
agents:
  # Use GPT-4 for complex tutoring
  - class: io.tutoros.agents.SocraticTutorAgent
    temperature: 0.7
    max-tokens: 2048
    
  # Use GPT-3.5 for simple content generation
  - class: io.tutoros.agents.ContentAgent
    model: gpt-3.5-turbo
    temperature: 0.5
    max-tokens: 1024
```

### 4. Monitor Usage

Track token usage in logs:

```properties
logging.level.io.squados.trace=DEBUG
```

Check Redis for cumulative token count:

```bash
redis-cli GET squados:traces:tokens
```

---

## Switching Between OpenAI and Ollama

### Use OpenAI (Production)

In `tutor-api/pom.xml`:
```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

In `application.properties`:
```properties
squad.llm.provider=openai
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o
```

### Use Ollama (Local Dev)

In `tutor-api/pom.xml`:
```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

In `application.properties`:
```properties
squad.llm.provider=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=llama3.2
```

Then start Ollama:
```bash
ollama pull llama3.2
ollama serve
```

---

## Troubleshooting

### Error: "ChatClient.Builder not available"

**Cause:** OpenAI dependency not on classpath or API key not set.

**Fix:**
1. Verify `spring-ai-openai-spring-boot-starter` is in pom.xml
2. Run `mvn clean install`
3. Verify `OPENAI_API_KEY` environment variable is set

### Error: "Incorrect API key provided"

**Fix:**
```bash
# Check if key is set
echo $OPENAI_API_KEY

# Set it correctly
export OPENAI_API_KEY="sk-proj-your-actual-key"
```

### Error: "Rate limit exceeded"

**Fix:**
1. Add rate limiting in application.properties
2. Upgrade OpenAI plan
3. Enable caching to reduce API calls

### High Costs

**Fix:**
1. Switch to `gpt-3.5-turbo` for development
2. Enable caching
3. Reduce `max-tokens` per agent
4. Use rate limiting

---

## API Key Security

### DO NOT commit API keys to git

Add to `.gitignore`:
```
.env
application-local.properties
```

### Use environment variables

```bash
# Development
export OPENAI_API_KEY="sk-proj-..."

# Production (Docker)
docker run -e OPENAI_API_KEY="sk-proj-..." tutor-os

# Production (Kubernetes)
kubectl create secret generic openai-secret \
  --from-literal=api-key="sk-proj-..."
```

### Use Spring profiles

Create `application-prod.properties`:
```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

Run with profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## Pricing Calculator

Estimate your costs at: https://openai.com/pricing

**Example: 1000 tutoring sessions/day**
- Average 500 tokens input + 1000 tokens output per session
- Using gpt-4o-mini: ~$0.68/day = ~$20/month
- Using gpt-4o: ~$11.25/day = ~$337/month
- Using GPT-3.5-turbo: ~$0.75/day = ~$22.50/month

**With 50% cache hit rate:**
- gpt-4o-mini: ~$0.34/day = ~$10/month
- gpt-4o: ~$5.63/day = ~$169/month
- GPT-3.5-turbo: ~$0.38/day = ~$11.25/month

---

## Support

- OpenAI API Docs: https://platform.openai.com/docs
- Spring AI Docs: https://docs.spring.io/spring-ai/reference/
- SquadOS Docs: https://github.com/sgpatel/squad-os
