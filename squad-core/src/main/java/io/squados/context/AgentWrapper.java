package io.squados.context;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.*;
import io.squados.config.SquadConfig;
import io.squados.guardrail.GuardrailEngine;
import io.squados.guardrail.GuardrailResult;
import io.squados.health.AgentCircuitBreaker;
import io.squados.llm.*;
import io.squados.memory.MemoryManager;
import io.squados.memory.store.MemoryRecord;
import io.squados.pipeline.ConditionEvaluator;
import io.squados.ratelimit.RateLimitEnforcer;
import io.squados.retry.RetryEngine;
import io.squados.trace.AgentSpan;
import io.squados.trace.SquadTracer;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Wraps a single @Agent-annotated class instance.
 *
 * execute(TaskContext) flow:
 *   1. @Condition check — skip if expression evaluates false
 *   2. Circuit breaker check
 *   3. @RateLimit check
 *   4. TokenBudget check
 *   5. @Guardrails input check
 *   6. @AgentMemory injection into system prompt
 *   7. [@Retry wrapper →] callLlm()
 *   8. @Guardrails output check
 *   9. Memory write-back
 *  10. Trace span
 *  11. Circuit breaker feedback
 *
 * callLlm() is the unit @Retry wraps — pre-checks are NOT retried.
 */
public class AgentWrapper {

    private final Object               instance;
    private final Class<?>             agentClass;
    private final Agent                annotation;
    private final AgentRole            role;
    private final String               name;
    private final LlmOptions           options;
    private final LlmPort              llm;
    private final TokenWriter          tokenWriter;   // non-null if @Streaming present

    // Optional injected collaborators (set by SquadContext after boot)
    private AgentCircuitBreaker  breaker;
    private RateLimitEnforcer    rateLimiter;
    private TokenBudget          tokenBudget;
    private GuardrailEngine      guardrailEngine;
    private MemoryManager        memoryManager;

    // Annotation cache
    private final Retry       retryAnn;
    private final RateLimit   rateLimitAnn;
    private final Guardrails  guardrailsAnn;
    private final Streaming   streamingAnn;
    private final AgentMemory agentMemoryAnn;
    private final Condition   conditionAnn;
    private final Timeout     timeoutAnn;

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
        this.retryAnn        = agentClass.getAnnotation(Retry.class);
        this.rateLimitAnn    = agentClass.getAnnotation(RateLimit.class);
        this.guardrailsAnn   = agentClass.getAnnotation(Guardrails.class);
        this.streamingAnn    = agentClass.getAnnotation(Streaming.class);
        this.agentMemoryAnn  = agentClass.getAnnotation(AgentMemory.class);
        this.conditionAnn    = agentClass.getAnnotation(Condition.class);
        this.timeoutAnn      = agentClass.getAnnotation(Timeout.class);

        // Pre-instantiate TokenWriter from @Streaming at construction time
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
     *
     * Pre-checks (not retried): condition, circuit breaker, rate limit, token budget, guardrails input
     * Retry wraps: callLlm()
     * Post-call: guardrails output, memory write-back, trace span, circuit breaker feedback
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

        // Step 3: @RateLimit check (pre-call, no token count yet)
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
            guardrailEngine.checkInput(guardrailsAnn,
                ctx.getTaskDescription() != null ? ctx.getTaskDescription() : "", name);
        }

        // Step 6: Build system prompt (with @AgentMemory injection)
        String systemPrompt = buildSystemPrompt(ctx);

        // Step 7: [@Retry →] callLlm()
        AgentResponse response;
        try {
            if (retryAnn != null) {
                LlmResponse raw = RetryEngine.execute(retryAnn, name,
                    () -> callLlm(systemPrompt, ctx.getTaskDescription()));
                response = AgentResponse.of(raw, role, name, start);
            } else {
                LlmResponse raw = callLlm(systemPrompt, ctx.getTaskDescription());
                response = AgentResponse.of(raw, role, name, start);
            }
        } catch (Exception e) {
            if (breaker != null) breaker.onFailure(role, e.getMessage());
            return AgentResponse.failure(role, name,
                "Execution failed: " + e.getMessage(), start);
        }

        // Step 8: @Guardrails output check
        if (guardrailEngine != null && guardrailsAnn != null && response.isSuccess()) {
            guardrailEngine.checkOutput(guardrailsAnn,
                response.content() != null ? response.content() : "", name);
        }

        // Step 9: Token budget record
        if (tokenBudget != null) {
            tokenBudget.record(role, response.totalTokens());
        }

        // Step 10: Trace span
        recordTrace(ctx, response);

        // Step 11: Circuit breaker feedback
        if (breaker != null) {
            if (response.isSuccess()) breaker.onSuccess(role, response.latency().toMillis());
            else breaker.onFailure(role, response.errorMessage());
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

    // ── callLlm() — the unit that @Retry wraps ────────────────────────

    private LlmResponse callLlm(String systemPrompt, String userMessage) {
        if (streamingAnn != null && tokenWriter != null) {
            // Collect streamed tokens into a single response
            StringBuilder collected = new StringBuilder();
            llm.chatStream(systemPrompt, userMessage, options, token -> {
                tokenWriter.write(token);
                collected.append(token.text());
            });
            return new LlmResponse(collected.toString(), 0, collected.length(), options.model());
        }
        return llm.chat(systemPrompt, userMessage, options);
    }

    // ── System prompt ─────────────────────────────────────────────────

    /**
     * Build the system prompt, optionally injecting @AgentMemory records.
     */
    public String buildSystemPrompt(TaskContext ctx) {
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

        // @AgentMemory — inject retrieved memories
        if (agentMemoryAnn != null && memoryManager != null) {
            try {
                // Build a pseudo Memory annotation parameters
                List<io.squados.memory.store.MemoryRecord> memories =
                    retrieveMemories(ctx.getTaskDescription(), agentMemoryAnn);
                if (!memories.isEmpty()) {
                    sb.append("\n\n## Relevant Memories\n");
                    for (MemoryRecord rec : memories) {
                        sb.append("- ").append(rec.getContent()).append("\n");
                    }
                }
            } catch (Exception e) {
                // Memory errors must not break agent execution
            }
        }

        return sb.toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public void setBreaker(AgentCircuitBreaker b)        { this.breaker        = b; }
    public void setRateLimiter(RateLimitEnforcer r)      { this.rateLimiter    = r; }
    public void setTokenBudget(TokenBudget tb)           { this.tokenBudget    = tb; }
    public void setGuardrailEngine(GuardrailEngine ge)   { this.guardrailEngine= ge; }
    public void setMemoryManager(MemoryManager mm)       { this.memoryManager  = mm; }

    public AgentRole  getRole()        { return role; }
    public String     getName()        { return name; }
    public LlmOptions getOptions()     { return options; }
    public Object     getInstance()    { return instance; }
    public Class<?>   getAgentClass()  { return agentClass; }
    public Retry      getRetryAnn()    { return retryAnn; }
    public Streaming  getStreamingAnn(){ return streamingAnn; }
    public boolean    hasStreaming()   { return streamingAnn != null; }

    // ── Helpers ───────────────────────────────────────────────────────

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

    private List<io.squados.memory.store.MemoryRecord> retrieveMemories(
            String query, AgentMemory ann) {
        if (memoryManager == null || query == null) return List.of();
        // Use working memory retrieval via MemoryManager
        // We construct a temporary Memory annotation proxy
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
            public String[] tags()  { return new String[]{}; }
        };
    }

    private void recordTrace(TaskContext ctx, AgentResponse response) {
        try {
            AgentSpan span = AgentSpan.builder(name)
                .agentRole(role)
                .agentName(name)
                .status(response.isSuccess() ? AgentSpan.Status.OK : AgentSpan.Status.ERROR)
                .durationMs(response.latency().toMillis())
                .inputLength(ctx.getTaskDescription() != null
                    ? ctx.getTaskDescription().length() : 0)
                .outputLength(response.content() != null
                    ? response.content().length() : 0)
                .promptTokens(response.promptTokens())
                .completionTokens(response.completionTokens())
                .build();
            SquadTracer.getExporter().export(span);
        } catch (Exception e) {
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
