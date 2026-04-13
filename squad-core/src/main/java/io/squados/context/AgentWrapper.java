package io.squados.context;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.*;
import io.squados.cache.CacheEngine;
import io.squados.checkpoint.CheckpointEngine;
import io.squados.config.SquadConfig;
import io.squados.conversation.ConversationStore;
import io.squados.cost.CostAwareLlmPort;
import io.squados.cost.CostTracker;
import io.squados.durable.DurableStore;
import io.squados.exception.AgentTimeoutException;
import io.squados.guardrail.GuardrailEngine;
import io.squados.health.AgentCircuitBreaker;
import io.squados.llm.*;
import io.squados.mcp.McpToolProvider;
import io.squados.memory.MemoryManager;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.store.MemoryRecord;
import io.squados.metrics.MetricsPort;
import io.squados.pipeline.ConditionEvaluator;
import io.squados.ratelimit.RateLimitEnforcer;
import io.squados.reflexion.ReflexionEngine;
import io.squados.reflexion.ReflexionResult;
import io.squados.retry.RetryEngine;
import io.squados.structured.JsonSchemaGenerator;
import io.squados.structured.StructuredOutputParser;
import io.squados.structured.StructuredOutputResult;
import io.squados.trace.AgentSpan;
import io.squados.trace.SquadTracer;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wraps a single @Agent-annotated class instance.
 *
 * execute(TaskContext) flow:
 *   1.  @Condition check — skip if expression evaluates false
 *   2.  Circuit breaker check
 *   3.  @RateLimit check
 *   4.  TokenBudget check
 *   5.  @Guardrails input check
 *   6.  @Cache lookup — return cached response if hit (zero tokens)
 *   7.  @Checkpoint lookup — skip LLM if checkpoint exists in DurableStore
 *   8.  Build system prompt (@PromptTemplate, @AgentMemory, @McpServer)
 *   9.  [@Retry →] callLlm() [wrapped with @Timeout deadline]
 *  10.  @Cache store
 *  11.  @Checkpoint save
 *  12.  @Guardrails output check
 *  13.  Token budget record
 *  14.  @Observe metrics emission
 *  15.  Trace span
 *  16.  Circuit breaker feedback
 *
 * callLlm() is the unit @Retry wraps — pre-checks (1-7) are NOT retried.
 */
public class AgentWrapper {

    private final Object              instance;
    private final Class<?>            agentClass;
    private final Agent               annotation;
    private final AgentRole           role;
    private final String              name;
    private final LlmOptions          options;
    private final LlmPort             llm;
    private final TokenWriter         tokenWriter;   // non-null if @Streaming present

    // Optional injected collaborators (set by SquadContext after boot)
    private AgentCircuitBreaker  breaker;
    private RateLimitEnforcer    rateLimiter;
    private TokenBudget          tokenBudget;
    private GuardrailEngine      guardrailEngine;
    private MemoryManager        memoryManager;
    private ConversationStore    conversationStore;
    private McpToolProvider      mcpToolProvider;
    private MetricsPort          metricsPort;
    private EmbeddingPort        embeddingPort;    // for @Cache SEMANTIC mode
    private DurableStore         durableStore;     // for @Checkpoint
    private ReflexionEngine      reflexionEngine;  // for @Reflexion
    private CostTracker          costTracker;      // for @CostPolicy
    private CostAwareLlmPort     costAwareLlm;     // set by applyCostPolicy()

    // Annotation cache
    private final Retry          retryAnn;
    private final RateLimit      rateLimitAnn;
    private final Guardrails     guardrailsAnn;
    private final Streaming      streamingAnn;
    private final AgentMemory    agentMemoryAnn;
    private final Condition      conditionAnn;
    private final Timeout        timeoutAnn;
    private final McpServer      mcpServerAnn;
    private final Cache          cacheAnn;
    private final Observe        observeAnn;
    private final PromptTemplate promptTemplateAnn;
    private final Checkpoint     checkpointAnn;    // class-level @Checkpoint (not used yet)
    private final Reflexion        reflexionAnn;
    private final CostPolicy       costPolicyAnn;
    private final StructuredOutput structuredOutputAnn;

    // Lazily created CacheEngine (per-instance)
    private CacheEngine cacheEngine;

    // ── Construction ──────────────────────────────────────────────────

    public AgentWrapper(Class<?> agentClass,
                        SquadConfig.AgentConfig agentConfig,
                        SquadConfig squadConfig,
                        LlmPort llm) {
        this.agentClass  = agentClass;
        this.annotation  = agentClass.getAnnotation(Agent.class);
        this.role        = annotation.role();
        this.name        = resolveName(agentClass, annotation);
        this.llm         = llm;

        // Cache annotations
        this.retryAnn          = agentClass.getAnnotation(Retry.class);
        this.rateLimitAnn      = agentClass.getAnnotation(RateLimit.class);
        this.guardrailsAnn     = agentClass.getAnnotation(Guardrails.class);
        this.streamingAnn      = agentClass.getAnnotation(Streaming.class);
        this.agentMemoryAnn    = agentClass.getAnnotation(AgentMemory.class);
        this.conditionAnn      = agentClass.getAnnotation(Condition.class);
        this.timeoutAnn        = agentClass.getAnnotation(Timeout.class);
        this.mcpServerAnn      = agentClass.getAnnotation(McpServer.class);
        this.cacheAnn          = agentClass.getAnnotation(Cache.class);
        this.observeAnn        = agentClass.getAnnotation(Observe.class);
        this.promptTemplateAnn = agentClass.getAnnotation(PromptTemplate.class);
        this.checkpointAnn     = agentClass.getAnnotation(Checkpoint.class);
        this.reflexionAnn        = agentClass.getAnnotation(Reflexion.class);
        this.costPolicyAnn       = agentClass.getAnnotation(CostPolicy.class);
        this.structuredOutputAnn = agentClass.getAnnotation(StructuredOutput.class);

        // Pre-instantiate CacheEngine if @Cache is present
        if (cacheAnn != null) {
            this.cacheEngine = new CacheEngine(cacheAnn.mode(), cacheAnn.minScore());
        }

        // Pre-instantiate TokenWriter from @Streaming
        this.tokenWriter = resolveTokenWriter();

        // Resolve effective LlmOptions: yml overrides > role defaults
        this.options = (agentConfig != null)
                ? agentConfig.resolveOptions(role, squadConfig.getLlm().getModel())
                : role.defaultOptions().withModel(squadConfig.getLlm().getModel());

        // Instantiate via no-arg constructor
        try {
            var ctor = agentClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            this.instance = ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                "[SquadOS] Failed to instantiate agent '" + name
                + "'. Ensure it has a public no-arg constructor. Cause: "
                + e.getMessage(), e
            );
        }
    }

    /**
     * Invoke @PostConstruct method on the agent instance.
     */
    public void callPostConstruct() {
        for (Method m : agentClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(PostConstruct.class)) {
                if (m.getParameterCount() != 0) {
                    throw new RuntimeException(
                        "[SquadOS] @PostConstruct method '" + m.getName()
                        + "' in " + name + " must have no parameters."
                    );
                }
                try {
                    m.setAccessible(true);
                    m.invoke(instance);
                    return;
                } catch (Exception e) {
                    throw new RuntimeException(
                        "[SquadOS] @PostConstruct failed in agent '"
                        + name + "': " + e.getCause().getMessage(),
                        e.getCause()
                    );
                }
            }
        }
    }

    // ── Task execution ────────────────────────────────────────────────

    /**
     * Full execution flow with all annotations applied.
     */
    public AgentResponse execute(TaskContext ctx) {
        Instant start = Instant.now();

        // Step 1: @Condition — skip if expression evaluates false
        if (conditionAnn != null) {
            boolean condMet = ConditionEvaluator.evaluate(
                conditionAnn.expression(), ctx.getTaskDescription(), true);
            if (!condMet) {
                return AgentResponse.skipped(role, name);
            }
        }

        // Step 2: Circuit breaker check
        if (breaker != null && !breaker.allowCall(role)) {
            return AgentResponse.failure(role, name,
                "Circuit open — " + name + " unavailable.", start);
        }

        // Step 3: @RateLimit check
        if (rateLimiter != null && rateLimitAnn != null) {
            rateLimiter.checkAndConsume(role, name, rateLimitAnn, options.maxTokens());
        }

        // Step 4: TokenBudget check
        if (tokenBudget != null && !tokenBudget.hasRemaining(role)) {
            return AgentResponse.failure(role, name,
                "Token budget exhausted for " + name, start);
        }

        // Step 5: @Guardrails input check
        if (guardrailEngine != null && guardrailsAnn != null) {
            try {
                guardrailEngine.checkInput(guardrailsAnn,
                    ctx.getTaskDescription() != null ? ctx.getTaskDescription() : "", name);
            } catch (io.squados.exception.GuardrailException ge) {
                return AgentResponse.failure(role, name, ge.getMessage(), start);
            }
        }

        // Step 6: @Cache lookup — return immediately on hit
        String cacheKey = null;
        if (cacheEngine != null) {
            // Build key before system prompt to keep it stable across retries
            cacheKey = cacheEngine.buildKey(annotation.description(), ctx.getTaskDescription());
            Optional<String> cached = cacheEngine.get(cacheKey);
            if (cached.isPresent()) {
                return AgentResponse.success(role, name, cached.get());
            }
        }

        // Step 7: @Checkpoint lookup — skip LLM if checkpoint was already saved
        String checkpointName = resolveCheckpointName();
        if (checkpointName != null && durableStore != null) {
            CheckpointEngine ce = new CheckpointEngine(durableStore);
            Optional<String> cp = ce.getCheckpoint(ctx.getTaskId(), name, checkpointName);
            if (cp.isPresent()) {
                return AgentResponse.success(role, name, cp.get());
            }
        }

        // Step 8: Build system prompt (@PromptTemplate, @AgentMemory, @McpServer)
        String systemPrompt = buildSystemPrompt(ctx);

        // Step 9: [@Retry →] callLlm() [wrapped with @Timeout]
        AgentResponse response;
        try {
            if (retryAnn != null) {
                LlmResponse raw = RetryEngine.execute(retryAnn, name,
                    () -> callLlmWithTimeout(systemPrompt, ctx.getTaskDescription()));
                response = AgentResponse.of(raw, role, name, start);
            } else {
                LlmResponse raw = callLlmWithTimeout(systemPrompt, ctx.getTaskDescription());
                response = AgentResponse.of(raw, role, name, start);
            }
        } catch (AgentTimeoutException te) {
            if (breaker != null) breaker.onFailure(role, te.getMessage());
            emitErrorMetrics("timeout");
            return AgentResponse.failure(role, name, te.getMessage(), start);
        } catch (Exception e) {
            if (breaker != null) breaker.onFailure(role, e.getMessage());
            emitErrorMetrics("llm");
            return AgentResponse.failure(role, name,
                "Execution failed: " + e.getMessage(), start);
        }

        // Step 9b: @Reflexion — score, critique, and optionally iterate
        if (reflexionAnn != null && reflexionEngine != null && response.isSuccess()
                && response.content() != null) {
            try {
                ReflexionResult reflexionResult = reflexionEngine.run(
                    reflexionAnn, response.content(), systemPrompt, ctx.getTaskDescription());
                // Replace response content with the best Reflexion output
                response = AgentResponse.success(role, name, reflexionResult.finalOutput());
            } catch (Exception e) {
                // Reflexion errors must not break execution
                System.err.println("[SquadOS] Reflexion failed for " + name + ": " + e.getMessage());
            }
        }

        // Step 9c: @StructuredOutput — parse LLM text into typed POJO
        if (structuredOutputAnn != null && response.isSuccess() && response.content() != null) {
            try {
                StructuredOutputResult<?> parsed = StructuredOutputParser.parse(
                    response.content(),
                    structuredOutputAnn.schema(),
                    structuredOutputAnn.retryOnMalformed(),
                    structuredOutputAnn.maxRetries(),
                    systemPrompt,
                    effectiveLlm(),
                    options);
                response = response.withStructuredOutput(parsed);
            } catch (Exception e) {
                System.err.println("[SquadOS] StructuredOutput parse failed for " + name + ": " + e.getMessage());
                return AgentResponse.failure(role, name,
                    "StructuredOutput parse failed: " + e.getMessage(), start);
            }
        }

        // Step 10: @Cache store
        if (cacheEngine != null && cacheKey != null && response.isSuccess()
                && response.content() != null) {
            cacheEngine.put(cacheKey, response.content(), cacheAnn.ttlSeconds());
        }

        // Step 11: @Checkpoint save
        if (checkpointName != null && durableStore != null && response.isSuccess()
                && response.content() != null) {
            new CheckpointEngine(durableStore)
                .saveCheckpoint(ctx.getTaskId(), name, checkpointName, response.content(), role);
        }

        // Step 12: @Guardrails output check
        if (guardrailEngine != null && guardrailsAnn != null && response.isSuccess()) {
            guardrailEngine.checkOutput(guardrailsAnn,
                response.content() != null ? response.content() : "", name);
        }

        // Step 13: Token budget record
        if (tokenBudget != null) {
            tokenBudget.record(role, response.totalTokens());
        }

        // Step 14: @Observe — emit metrics
        if (observeAnn != null && metricsPort != null) {
            long latencyMs = response.latency() != null ? response.latency().toMillis() : 0L;
            String status  = response.isSuccess() ? "success" : "error";
            metricsPort.recordCall(name, role.name(), status, latencyMs,
                observeAnn.namespace(), observeAnn.tags());
            metricsPort.recordTokens(name, role.name(), "prompt",
                response.promptTokens(), observeAnn.namespace(), observeAnn.tags());
            metricsPort.recordTokens(name, role.name(), "completion",
                response.completionTokens(), observeAnn.namespace(), observeAnn.tags());
        }

        // Step 15: Trace span
        recordTrace(ctx, response);

        // Step 16: Circuit breaker feedback
        if (breaker != null) {
            if (response.isSuccess()) breaker.onSuccess(role, response.latency().toMillis());
            else                      breaker.onFailure(role, response.errorMessage());
        }

        return response;
    }

    /**
     * Execute with streaming — emits tokens to the agent's TokenWriter.
     */
    public void executeStream(TaskContext ctx, Consumer<StreamToken> handler) {
        String systemPrompt = buildSystemPrompt(ctx);
        TokenWriter writer = handler != null
            ? token -> handler.accept(token)
            : (tokenWriter != null ? tokenWriter : new NoOpTokenWriter());

        llm.chatStream(systemPrompt, ctx.getTaskDescription(), options, writer);
    }

    // ── callLlm — the unit @Retry wraps ──────────────────────────────

    /**
     * Wraps callLlm() with @Timeout enforcement using CompletableFuture.orTimeout().
     * Falls back to synchronous call if no @Timeout is present.
     */
    private LlmResponse callLlmWithTimeout(String systemPrompt, String userMessage) {
        if (timeoutAnn == null) {
            return callLlm(systemPrompt, userMessage);
        }
        try {
            return CompletableFuture
                .supplyAsync(() -> callLlm(systemPrompt, userMessage))
                .orTimeout(timeoutAnn.timeoutMs(), TimeUnit.MILLISECONDS)
                .get();
        } catch (java.util.concurrent.ExecutionException e) {
            // orTimeout() completes the future exceptionally with TimeoutException
            // which then surfaces as ExecutionException.getCause()
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                if ("fallback".equals(timeoutAnn.action())
                        && !timeoutAnn.fallbackResponse().isBlank()) {
                    return new LlmResponse(timeoutAnn.fallbackResponse(), 0, 0, options.model());
                }
                throw new AgentTimeoutException(name, timeoutAnn.timeoutMs());
            }
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentTimeoutException(name, timeoutAnn.timeoutMs());
        }
    }

    private LlmResponse callLlm(String systemPrompt, String userMessage) {
        // Conversation history — load if ConversationStore is wired
        List<ConversationMessage> history = List.of();
        String sessionId = null;
        if (conversationStore != null) {
            sessionId = name; // per-agent session key
            history   = conversationStore.getHistory(sessionId);
        }

        LlmPort activeLlm = effectiveLlm();
        LlmResponse response;
        if (streamingAnn != null && tokenWriter != null) {
            // Collect streamed tokens into a single response
            StringBuilder collected = new StringBuilder();
            activeLlm.chatStream(systemPrompt, userMessage, options, token -> {
                tokenWriter.write(token);
                if (!token.isLast()) collected.append(token.text());
            });
            response = new LlmResponse(collected.toString(), 0, collected.length(), options.model());
        } else if (conversationStore != null && !history.isEmpty()) {
            response = activeLlm.chatWithHistory(systemPrompt, userMessage, history, options);
        } else {
            response = activeLlm.chat(systemPrompt, userMessage, options);
        }

        // Append turn to conversation history
        if (conversationStore != null && sessionId != null && response.content() != null) {
            conversationStore.append(sessionId, ConversationMessage.user(userMessage));
            conversationStore.append(sessionId, ConversationMessage.assistant(response.content()));
        }

        return response;
    }

    // ── System prompt ─────────────────────────────────────────────────

    /**
     * Build the system prompt.
     * Priority: @PromptTemplate file → default inline prompt.
     * Always appended: @McpServer tools, @AgentMemory records.
     */
    public String buildSystemPrompt(TaskContext ctx) {
        String base;

        // @PromptTemplate — load from classpath with {{variable}} substitution
        if (promptTemplateAnn != null) {
            base = loadAndSubstituteTemplate(promptTemplateAnn, ctx);
        } else {
            // Default inline system prompt
            StringBuilder sb = new StringBuilder();
            sb.append("You are ").append(name)
              .append(", a ").append(role).append(" agent");
            if (!annotation.description().isBlank()) {
                sb.append(".\n").append(annotation.description());
            } else {
                sb.append(".");
            }
            if (!annotation.profile().isBlank()
                    && !annotation.profile().equals("default")) {
                sb.append("\nActive profile: ").append(annotation.profile()).append(".");
            }
            sb.append("\nTask ID: ").append(ctx.getTaskId()).append(".");
            base = sb.toString();
        }

        StringBuilder sb = new StringBuilder(base);

        // @McpServer — discover and inject tool definitions
        if (mcpServerAnn != null && mcpToolProvider != null
                && mcpServerAnn.urls().length > 0) {
            try {
                List<io.squados.mcp.McpToolDefinition> allTools = new java.util.ArrayList<>();
                for (String url : mcpServerAnn.urls()) {
                    allTools.addAll(mcpToolProvider.discoverTools(url));
                }
                if (!allTools.isEmpty()) {
                    sb.append(mcpToolProvider.buildMcpPrompt(allTools));
                }
            } catch (Exception ignored) {
                // MCP errors must not break execution
            }
        }

        // @AgentMemory — inject retrieved memories
        if (agentMemoryAnn != null && memoryManager != null) {
            try {
                List<MemoryRecord> memories =
                    retrieveMemories(ctx.getTaskDescription(), agentMemoryAnn);
                if (!memories.isEmpty()) {
                    sb.append("\n\n## Relevant Memories\n");
                    for (MemoryRecord rec : memories) {
                        sb.append("- ").append(rec.getContent()).append("\n");
                    }
                }
            } catch (Exception ignored) {
                // Memory errors must not break execution
            }
        }

        // @StructuredOutput — append JSON schema instructions
        if (structuredOutputAnn != null) {
            String schemaPrompt = JsonSchemaGenerator.buildSchemaPrompt(structuredOutputAnn.schema());
            if (structuredOutputAnn.inject() == StructuredOutput.SchemaInjection.PREPEND) {
                sb.insert(0, schemaPrompt);
            } else {
                sb.append(schemaPrompt);
            }
        }

        return sb.toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public void setBreaker(AgentCircuitBreaker b)        { this.breaker          = b; }
    public void setRateLimiter(RateLimitEnforcer r)      { this.rateLimiter      = r; }
    public void setTokenBudget(TokenBudget tb)           { this.tokenBudget      = tb; }
    public void setGuardrailEngine(GuardrailEngine ge)   { this.guardrailEngine  = ge; }
    public void setMemoryManager(MemoryManager mm)       { this.memoryManager    = mm; }
    public void setConversationStore(ConversationStore s){ this.conversationStore = s; }
    public void setMcpToolProvider(McpToolProvider p)    { this.mcpToolProvider  = p; }
    public void setMetricsPort(MetricsPort mp)           { this.metricsPort      = mp; }
    public void setDurableStore(DurableStore ds)         { this.durableStore     = ds; }
    public void setEmbeddingPort(EmbeddingPort ep) {
        this.embeddingPort = ep;
        if (cacheEngine != null) cacheEngine.setEmbeddingPort(ep);
    }
    public void setReflexionEngine(ReflexionEngine re)   { this.reflexionEngine  = re; }

    /**
     * Inject a {@link CostTracker} and wrap the agent's LlmPort with
     * {@link CostAwareLlmPort} if {@code @CostPolicy} is present.
     * No-op if neither {@code @CostPolicy} nor a fallback model is configured.
     */
    public void setCostTracker(CostTracker ct) {
        this.costTracker = ct;
        // CostPolicy wrapping is applied to the LlmPort in the AgentWrapper constructor
        // after all setters have been called — nothing more needed here.
    }

    public CostPolicy getCostPolicyAnn()                 { return costPolicyAnn; }

    /**
     * Wrap this agent's LlmPort with a {@link CostAwareLlmPort}.
     * Called by SquadContext.boot() after all wrappers are fully constructed.
     * The AgentWrapper.llm field is final — wrapping is applied by reassigning
     * the effective LLM used in callLlm() via a stored override.
     */
    public void applyCostPolicy(CostPolicy policy, CostTracker tracker, LlmPort baseLlm) {
        this.costAwareLlm = new CostAwareLlmPort(baseLlm, tracker, policy, name);
    }

    /** Returns the effective LlmPort to use — cost-aware if @CostPolicy is present. */
    private LlmPort effectiveLlm() {
        return costAwareLlm != null ? costAwareLlm : llm;
    }

    public AgentRole  getRole()        { return role; }
    public String     getName()        { return name; }
    public LlmOptions getOptions()     { return options; }
    public Object     getInstance()    { return instance; }
    public Class<?>   getAgentClass()  { return agentClass; }
    public Retry      getRetryAnn()    { return retryAnn; }
    public Streaming  getStreamingAnn(){ return streamingAnn; }
    public boolean    hasStreaming()   { return streamingAnn != null; }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Find the @Checkpoint name for this agent's first @Checkpoint-annotated method,
     * or use the class-level @Checkpoint.name() if present.
     * Returns null if no checkpoint is configured.
     */
    private String resolveCheckpointName() {
        // Class-level @Checkpoint (unusual but supported)
        if (checkpointAnn != null) return checkpointAnn.name();
        // First @Checkpoint method (method-level is the primary use case)
        for (Method m : agentClass.getDeclaredMethods()) {
            Checkpoint ann = m.getAnnotation(Checkpoint.class);
            if (ann != null) return ann.name();
        }
        return null;
    }

    /**
     * Load a @PromptTemplate file from classpath and substitute {{variables}}.
     * Built-in variables: {{agentName}}, {{role}}, {{description}}, {{profile}}.
     */
    private String loadAndSubstituteTemplate(PromptTemplate ann, TaskContext ctx) {
        try (InputStream is = AgentWrapper.class.getClassLoader()
                .getResourceAsStream(ann.path())) {
            if (is == null) {
                System.err.println("[SquadOS] @PromptTemplate file not found: " + ann.path()
                    + " — falling back to default system prompt.");
                return buildDefaultPrompt(ctx);
            }
            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // Substitute built-in variables
            template = template
                .replace("{{agentName}}",   name)
                .replace("{{role}}",        role.name())
                .replace("{{description}}", annotation.description())
                .replace("{{profile}}",     annotation.profile())
                .replace("{{taskId}}",      ctx.getTaskId() != null ? ctx.getTaskId() : "");
            return template;
        } catch (Exception e) {
            System.err.println("[SquadOS] Failed to load @PromptTemplate '" + ann.path()
                + "': " + e.getMessage() + " — using default prompt.");
            return buildDefaultPrompt(ctx);
        }
    }

    private String buildDefaultPrompt(TaskContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(name).append(", a ").append(role).append(" agent");
        if (!annotation.description().isBlank()) {
            sb.append(".\n").append(annotation.description());
        } else {
            sb.append(".");
        }
        sb.append("\nTask ID: ").append(ctx.getTaskId()).append(".");
        return sb.toString();
    }

    private TokenWriter resolveTokenWriter() {
        if (streamingAnn == null) return null;
        Class<?> writerClass = streamingAnn.writer();
        try {
            var ctor = writerClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (TokenWriter) ctor.newInstance();
        } catch (Exception e) {
            return new StdoutTokenWriter();
        }
    }

    private List<MemoryRecord> retrieveMemories(String query, AgentMemory ann) {
        if (memoryManager == null || query == null) return List.of();
        io.squados.memory.annotation.Memory memAnn =
            buildMemoryAnnotation(ann.topK(), ann.minScore(), ann.scope());
        try {
            return memoryManager.read(memAnn, name, null, null, query);
        } catch (Exception e) {
            return List.of();
        }
    }

    private io.squados.memory.annotation.Memory buildMemoryAnnotation(
            int topK, float minScore, String scope) {
        return new io.squados.memory.annotation.Memory() {
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return io.squados.memory.annotation.Memory.class;
            }
            public io.squados.memory.annotation.MemoryOp op() {
                return io.squados.memory.annotation.MemoryOp.READ;
            }
            public io.squados.memory.annotation.MemoryType type() {
                return io.squados.memory.annotation.MemoryType.EPISODIC;
            }
            public io.squados.memory.annotation.MemoryScope scope() {
                return "squad".equals(scope)
                    ? io.squados.memory.annotation.MemoryScope.SQUAD
                    : io.squados.memory.annotation.MemoryScope.AGENT;
            }
            public int topK()       { return topK; }
            public float minScore() { return minScore; }
            public io.squados.memory.annotation.Importance importance() {
                return io.squados.memory.annotation.Importance.MEDIUM;
            }
            public String[] tags()   { return new String[]{}; }
            public boolean promote() { return false; }
        };
    }

    private void emitErrorMetrics(String errorType) {
        if (observeAnn != null && metricsPort != null) {
            metricsPort.recordError(name, role.name(), errorType,
                observeAnn.namespace(), observeAnn.tags());
        }
    }

    private void recordTrace(TaskContext ctx, AgentResponse response) {
        try {
            AgentSpan span = AgentSpan.builder(name)
                .agentRole(role)
                .agentName(name)
                .status(response.isSuccess() ? AgentSpan.Status.OK : AgentSpan.Status.ERROR)
                .durationMs(response.latency() != null ? response.latency().toMillis() : 0L)
                .inputLength(ctx.getTaskDescription() != null
                    ? ctx.getTaskDescription().length() : 0)
                .outputLength(response.content() != null
                    ? response.content().length() : 0)
                .promptTokens(response.promptTokens())
                .completionTokens(response.completionTokens())
                .build();
            SquadTracer.getExporter().export(span);
        } catch (Exception ignored) {
            // Tracer must never break agent execution
        }
    }

    private static String resolveName(Class<?> cls, Agent ann) {
        return (ann.name() == null || ann.name().isBlank())
                ? cls.getSimpleName()
                : ann.name();
    }

    @Override
    public String toString() {
        return "AgentWrapper{name='" + name + "', role=" + role
                + ", options=" + options + "}";
    }
}
