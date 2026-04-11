package io.squados.context;

import io.squados.agent.AgentResponse;
import io.squados.agent.SquadPlanDeserialiser;
import io.squados.agent.TaskContext;
import io.squados.annotation.AgentRole;
import io.squados.annotation.Pipeline;
import io.squados.bus.AgentMessage;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.MessageType;
import io.squados.config.SquadConfig;
import io.squados.conversation.ConversationStore;
import io.squados.durable.DurableEngine;
import io.squados.durable.DurableStore;
import io.squados.durable.InProcessDurableStore;
import io.squados.durable.WorkflowState;
import io.squados.exception.NoAgentFoundException;
import io.squados.execution.ParallelExecutor;
import io.squados.execution.SquadResult;
import io.squados.execution.SquadTask;
import io.squados.guardrail.GuardrailEngine;
import io.squados.health.AgentCircuitBreaker;
import io.squados.llm.LlmPort;
import io.squados.llm.StreamToken;
import io.squados.mcp.McpToolProvider;
import io.squados.memory.MemoryManager;
import io.squados.pipeline.PipelineEngine;
import io.squados.pipeline.PipelineResult;
import io.squados.ratelimit.RateLimitEnforcer;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The central container for a SquadOS application.
 *
 * Analogous to Spring's ApplicationContext:
 *   - Owns the agent lifecycle (scan → instantiate → init → execute)
 *   - Holds the AgentRegistry
 *   - Exposes the full submission API
 *
 * Usage (minimal):
 * <pre>
 *   SquadContext ctx = new SquadContext(config, llmPort);
 *   ctx.boot();
 *   AgentResponse r = ctx.submit("Plan the attack on north gate");
 * </pre>
 *
 * Usage (full):
 * <pre>
 *   SquadContext ctx = new SquadContext(config, llm, memoryManager, conversationStore,
 *       rateLimitEnforcer, tokenBudget, mcpToolProvider, durableStore);
 *   ctx.boot();
 * </pre>
 */
public class SquadContext {

    private final SquadConfig          config;
    private final LlmPort              llm;
    private final AgentRegistry        registry  = new AgentRegistry();
    private final AgentMessageBus      bus       = new AgentMessageBus();
    private final AgentCircuitBreaker  breaker   = new AgentCircuitBreaker(bus);
    private       ParallelExecutor     executor;
    private       PipelineEngine       pipelineEngine;
    private       DurableEngine        durableEngine;
    private       boolean              booted    = false;

    // Optional collaborators
    private       MemoryManager        memoryManager;
    private       ConversationStore    conversationStore;
    private       RateLimitEnforcer    rateLimitEnforcer;
    private       TokenBudget          tokenBudget;
    private       McpToolProvider      mcpToolProvider;
    private       DurableStore         durableStore;
    private       GuardrailEngine      guardrailEngine;

    // ── Construction ──────────────────────────────────────────────────

    /** Minimal constructor — only LLM required. */
    public SquadContext(SquadConfig config, LlmPort llm) {
        this.config = config;
        this.llm    = llm;
    }

    /** Full constructor with all optional collaborators. */
    public SquadContext(SquadConfig config, LlmPort llm,
                        MemoryManager memoryManager,
                        ConversationStore conversationStore,
                        RateLimitEnforcer rateLimitEnforcer,
                        TokenBudget tokenBudget,
                        McpToolProvider mcpToolProvider,
                        DurableStore durableStore) {
        this(config, llm);
        this.memoryManager      = memoryManager;
        this.conversationStore  = conversationStore;
        this.rateLimitEnforcer  = rateLimitEnforcer;
        this.tokenBudget        = tokenBudget;
        this.mcpToolProvider    = mcpToolProvider;
        this.durableStore       = durableStore;
    }

    // ── Boot sequence ─────────────────────────────────────────────────

    /**
     * Boot the SquadContext.
     *
     * Steps:
     *   1. Print boot banner
     *   2. Scan classpath for @Agent classes (explicit from squad.yml)
     *   3. Instantiate each agent, wrap with AgentWrapper
     *   4. Register in AgentRegistry
     *   5. Call @PostConstruct on each agent
     *   6. Inject collaborators into wrappers
     *   7. Initialise engines (ParallelExecutor, PipelineEngine, DurableEngine)
     */
    public void boot() {
        if (booted) return;

        printBanner();

        // Step 1: scan for @Agent classes
        List<Class<?>> agentClasses = AgentScanner.scan(config);

        // Step 2: instantiate, wrap, and register each agent
        for (Class<?> cls : agentClasses) {
            SquadConfig.AgentConfig agentCfg = config.forClass(cls);
            AgentWrapper wrapper = new AgentWrapper(cls, agentCfg, config, llm);
            registry.register(wrapper);
        }

        // Step 3: @PostConstruct
        for (AgentWrapper wrapper : registry.all()) {
            wrapper.callPostConstruct();
        }

        // Step 4: register with circuit breaker
        for (AgentWrapper wrapper : registry.all()) {
            breaker.register(wrapper.getRole(), wrapper.getName());
        }

        // Step 5: inject circuit breaker + optional collaborators into each wrapper
        for (AgentWrapper wrapper : registry.all()) {
            wrapper.setBreaker(breaker);
            if (rateLimitEnforcer != null) wrapper.setRateLimiter(rateLimitEnforcer);
            if (tokenBudget       != null) wrapper.setTokenBudget(tokenBudget);
            if (guardrailEngine   != null) wrapper.setGuardrailEngine(guardrailEngine);
            if (memoryManager     != null) wrapper.setMemoryManager(memoryManager);
        }

        // Step 6: @OnMessage listeners
        for (AgentWrapper wrapper : registry.all()) {
            bus.registerListeners(wrapper.getInstance());
        }

        // Step 7: initialise engines
        this.executor       = new ParallelExecutor(registry);
        this.pipelineEngine = new PipelineEngine(registry);
        this.durableStore   = (durableStore != null) ? durableStore : new InProcessDurableStore();
        this.durableEngine  = new DurableEngine(durableStore, registry);
        if (guardrailEngine == null) this.guardrailEngine = new GuardrailEngine();

        printRegistrationSummary();
        booted = true;
    }

    // ── Task submission ───────────────────────────────────────────────

    /** Submit a task to the lead agent. */
    public AgentResponse submit(String task) {
        ensureBooted();
        TaskContext ctx = new TaskContext(task, newSessionId(), config.getProfile());
        log("Submitting task [%s]: %s", ctx.getTaskId(), task);

        AgentWrapper lead = registry.getLead();
        if (lead == null) {
            throw new NoAgentFoundException("No agents registered. Call boot() first.");
        }

        if (!breaker.allowCall(lead.getRole())) {
            log("Circuit OPEN for %s — finding fallback agent", lead.getName());
            lead = registry.all().stream()
                .filter(w -> breaker.allowCall(w.getRole()))
                .findFirst()
                .orElse(lead);
        }

        AgentResponse response = lead.execute(ctx);
        log("Response from %s [%s]: %d tokens, %dms",
            response.agentName(),
            response.isSuccess() ? "OK" : "FAILED",
            response.totalTokens(),
            response.latency().toMillis());
        return response;
    }

    /** Submit a task to a specific agent role. */
    public AgentResponse submitTo(AgentRole role, String task) {
        ensureBooted();
        AgentWrapper target = requireAgent(role);
        TaskContext ctx = new TaskContext(task, newSessionId(), config.getProfile());
        return target.execute(ctx);
    }

    /** Execute a SquadTask — runs assigned roles in parallel. */
    public SquadResult execute(SquadTask task) {
        ensureBooted();
        return executor.execute(task);
    }

    /** Submit to lead and deserialise into a typed @SquadPlan object. */
    public <T> T submit(String input, Class<T> planClass) {
        ensureBooted();
        String enrichedInput = input + SquadPlanDeserialiser.buildSchemaPrompt(planClass);
        AgentResponse response = submit(enrichedInput);
        return SquadPlanDeserialiser.deserialise(response.content(), planClass);
    }

    /** Submit to a specific role and deserialise into a typed plan. */
    public <T> T submitTo(AgentRole role, String input, Class<T> planClass) {
        ensureBooted();
        String enrichedInput = input + SquadPlanDeserialiser.buildSchemaPrompt(planClass);
        AgentResponse response = submitTo(role, enrichedInput);
        return SquadPlanDeserialiser.deserialise(response.content(), planClass);
    }

    /** Submit with streaming — emits tokens to consumer as they arrive. */
    public void submitStream(String task, Consumer<StreamToken> handler) {
        ensureBooted();
        AgentWrapper lead = requireLead();
        TaskContext ctx = new TaskContext(task, newSessionId(), config.getProfile());
        lead.executeStream(ctx, handler);
    }

    /** Submit to a specific role with streaming. */
    public void submitStream(AgentRole role, String task, Consumer<StreamToken> handler) {
        ensureBooted();
        AgentWrapper target = requireAgent(role);
        TaskContext ctx = new TaskContext(task, newSessionId(), config.getProfile());
        target.executeStream(ctx, handler);
    }

    /**
     * Submit a durable workflow. Idempotent — same workflowId resumes from last checkpoint.
     * Completed workflows return cached result immediately.
     */
    public AgentResponse submitDurable(AgentRole role, String workflowId, String input) {
        ensureBooted();
        return durableEngine.submit(role, workflowId, input);
    }

    /** Execute a @Pipeline — returns PipelineResult with per-step outputs. */
    public PipelineResult submitPipeline(AgentRole role, String input) {
        ensureBooted();
        AgentWrapper orchestrator = requireAgent(role);
        Pipeline pipeline = orchestrator.getAgentClass().getAnnotation(Pipeline.class);
        if (pipeline == null) {
            throw new IllegalArgumentException(
                "Agent with role " + role + " is not annotated with @Pipeline");
        }
        return pipelineEngine.execute(pipeline, input, newSessionId());
    }

    /** Pause a durable workflow. */
    public void pauseWorkflow(String workflowId) {
        ensureBooted();
        durableEngine.pause(workflowId);
    }

    /** Resume a paused durable workflow. */
    public void resumeWorkflow(String workflowId) {
        ensureBooted();
        durableEngine.resume(workflowId);
    }

    /** Get the current state of a durable workflow. */
    public Optional<WorkflowState> getWorkflowState(String workflowId) {
        ensureBooted();
        return durableEngine.getState(workflowId);
    }

    // ── Post-boot setters ─────────────────────────────────────────────

    public void setDurableStore(DurableStore store) {
        this.durableStore  = store;
        this.durableEngine = new DurableEngine(store, registry);
    }

    public void setGuardrailEngine(GuardrailEngine engine) {
        this.guardrailEngine = engine;
        if (booted) {
            registry.all().forEach(w -> w.setGuardrailEngine(engine));
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public AgentMessageBus     getBus()            { ensureBooted(); return bus; }
    public AgentCircuitBreaker getBreaker()        { ensureBooted(); return breaker; }
    public AgentRegistry       getRegistry()       { ensureBooted(); return registry; }
    public SquadConfig         getConfig()         { return config; }
    public boolean             isBooted()          { return booted; }
    public GuardrailEngine     getGuardrailEngine(){ return guardrailEngine; }

    // ── Internals ─────────────────────────────────────────────────────

    private void ensureBooted() {
        if (!booted) {
            throw new IllegalStateException(
                "[SquadOS] SquadContext has not been booted. Call boot() first.");
        }
    }

    private AgentWrapper requireAgent(AgentRole role) {
        AgentWrapper w = registry.getByRole(role);
        if (w == null) {
            throw new IllegalArgumentException(
                "[SquadOS] No agent registered for role: " + role
                + ". Registered: " + registry.all().stream()
                    .map(a -> a.getRole().toString()).toList());
        }
        return w;
    }

    private AgentWrapper requireLead() {
        AgentWrapper lead = registry.getLead();
        if (lead == null) {
            throw new NoAgentFoundException("No agents registered. Call boot() first.");
        }
        return lead;
    }

    private String newSessionId() {
        return "sess-" + Long.toHexString(System.currentTimeMillis());
    }

    private void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════╗");
        System.out.println("  ║   SquadOS — Multi-Agent AI Framework for Java  ║");
        System.out.println("  ╚══════════════════════════════════════════════════╝");
        System.out.printf ("  Squad   : %s%n", config.getName());
        System.out.printf ("  Profile : %s%n", config.getProfile());
        System.out.printf ("  Provider: %s / %s%n",
            config.getLlm().getProvider(), config.getLlm().getModel());
        System.out.println();
    }

    private void printRegistrationSummary() {
        System.out.println("  Registered agents:");
        for (AgentWrapper w : registry.all()) {
            System.out.printf("    ✓ %-20s [%s]  temp=%.1f  maxTokens=%d%n",
                w.getName(), w.getRole(),
                w.getOptions().temperature(), w.getOptions().maxTokens());
        }
        System.out.printf("%n  SquadContext ready — %d agent(s) online.%n%n",
            registry.count());
    }

    private static void log(String fmt, Object... args) {
        System.out.printf("[SquadOS] " + fmt + "%n", args);
    }
}
