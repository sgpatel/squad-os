package io.squados.context;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.AgentRole;
import io.squados.config.SquadConfig;
import io.squados.config.SquadConfigParser;
import io.squados.exception.NoAgentFoundException;
import io.squados.bus.AgentMessageBus;
import io.squados.bus.MessageType;
import io.squados.bus.AgentMessage;
import io.squados.execution.ParallelExecutor;
import io.squados.agent.SquadPlanDeserialiser;
import io.squados.execution.SquadTask;
import io.squados.execution.SquadResult;
import io.squados.health.AgentCircuitBreaker;
import io.squados.llm.LlmPort;

import java.util.List;

/**
 * The central container for a SquadOS application.
 *
 * Analogous to Spring's ApplicationContext:
 *   - Owns the agent lifecycle (scan → instantiate → init → execute)
 *   - Holds the AgentRegistry
 *   - Exposes submit(task) as the single public execution entry point
 *
 * Usage:
 * <pre>
 *   SquadContext ctx = new SquadContext(config, llmPort);
 *   ctx.boot();
 *   AgentResponse r = ctx.submit("Plan the attack on north gate");
 *   System.out.println(r.content());
 * </pre>
 */
public class SquadContext {

    private final SquadConfig    config;
    private final LlmPort        llm;
    private final AgentRegistry  registry = new AgentRegistry();
    private final AgentMessageBus    bus      = new AgentMessageBus();
    private final AgentCircuitBreaker breaker   = new AgentCircuitBreaker(bus);
    private       ParallelExecutor    executor;
    private boolean booted = false;

    // ── Construction ──────────────────────────────────────────────────

    public SquadContext(SquadConfig config, LlmPort llm) {
        this.config = config;
        this.llm    = llm;
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
     *   6. Print registration summary
     */
    public void boot() {
        if (booted) return; // Idempotent

        printBanner();

        // Step 1: scan for @Agent classes
        List<Class<?>> agentClasses = AgentScanner.scan(config);

        // Step 2: instantiate, wrap, and register each agent
        for (Class<?> cls : agentClasses) {
            SquadConfig.AgentConfig agentCfg = config.forClass(cls);
            AgentWrapper wrapper = new AgentWrapper(cls, agentCfg, config, llm);
            registry.register(wrapper);
        }

        // Step 3: call @PostConstruct on all registered agents
        for (AgentWrapper wrapper : registry.all()) {
            wrapper.callPostConstruct();
        }

        // Step 3a: register agents with circuit breaker
        for (AgentWrapper wrapper : registry.all()) {
            breaker.register(wrapper.getRole(), wrapper.getName());
        }

        // Inject breaker into each wrapper so execute() can consult it
        for (AgentWrapper wrapper : registry.all()) {
            wrapper.setBreaker(breaker);
        }

        // Step 3b: register @OnMessage listeners on the bus
        for (AgentWrapper wrapper : registry.all()) {
            bus.registerListeners(wrapper.getInstance());
        }

        // Step 4: initialise parallel executor
        this.executor = new ParallelExecutor(registry);

        // Step 4: print summary
        printRegistrationSummary();
        booted = true;
    }

    // ── Task submission ───────────────────────────────────────────────

    /**
     * Submit a task to the lead agent.
     *
     * The lead agent is determined by AgentRegistry.getLead():
     * STRATEGIST > ANALYST > EXECUTOR > first registered.
     *
     * @param task  The task description string.
     * @return      AgentResponse from the lead agent.
     */
    public AgentResponse submit(String task) {
        ensureBooted();
        TaskContext ctx = new TaskContext(task, newSessionId(), config.getProfile());
        log("Submitting task [%s]: %s", ctx.getTaskId(), task);

        AgentWrapper lead = registry.getLead();
        if (lead == null) {
            throw new NoAgentFoundException("No agents registered. Call boot() first.");
        }

        // Circuit breaker check — if lead is open, try next available agent
        if (!breaker.allowCall(lead.getRole())) {
            log("Circuit OPEN for %s — finding fallback agent", lead.getName());
            lead = registry.all().stream()
                .filter(w -> breaker.allowCall(w.getRole()))
                .findFirst()
                .orElse(lead); // if all open, let it through to fail gracefully
        }

        AgentResponse response = lead.execute(ctx);
        log("Response from %s [%s]: %d tokens, %dms",
            response.agentName(),
            response.isSuccess() ? "OK" : "FAILED",
            response.totalTokens(),
            response.latency().toMillis());

        return response;
    }

    /**
     * Submit a task to a specific agent role.
     * Useful for routing tasks to non-lead agents directly.
     */
    public AgentResponse submitTo(AgentRole role, String task) {
        ensureBooted();
        AgentWrapper target = registry.getByRole(role);
        if (target == null) {
            throw new IllegalArgumentException(
                "[SquadOS] No agent registered for role: " + role
                + ". Registered roles: " + registry.all().stream()
                    .map(w -> w.getRole().toString())
                    .toList()
            );
        }
        TaskContext ctx = new TaskContext(task, newSessionId(), config.getProfile());
        return target.execute(ctx);
    }

    /**
     * Execute a SquadTask — runs assigned roles in parallel.
     * All agents fire simultaneously; result available when slowest finishes.
     *
     * Example:
     * <pre>
     * SquadResult result = ctx.execute(
     *     SquadTask.of("Analyse this code:\n" + code)
     *         .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR)
     *         .withLabel("Code Review")
     * );
     * System.out.println(result.get(AgentRole.ANALYST).content());
     * </pre>
     */
    public SquadResult execute(SquadTask task) {
        ensureBooted();
        return executor.execute(task);
    }

    /**
     * Submit a task and deserialise the response into a typed {@literal @}SquadPlan object.
     *
     * <pre>
     * DayPlan plan = ctx.submit("Plan my day:\n" + tasks, DayPlan.class);
     * plan.getDoToday()  // List<String>
     * plan.getVerdict()  // "Realistic"
     * </pre>
     *
     * @param input       The task input
     * @param planClass   Class annotated with {@literal @}SquadPlan
     * @return            Populated instance of planClass
     */
    public <T> T submit(String input, Class<T> planClass) {
        ensureBooted();
        // Build schema-aware prompt
        String schemaHint = SquadPlanDeserialiser.buildSchemaPrompt(planClass);
        String enrichedInput = input + schemaHint;
        // Submit to lead agent
        AgentResponse response = submit(enrichedInput);
        // Deserialise JSON response into typed object
        return SquadPlanDeserialiser.deserialise(response.content(), planClass);
    }

    /**
     * Submit to a specific role and deserialise into a typed plan.
     */
    public <T> T submitTo(AgentRole role, String input, Class<T> planClass) {
        ensureBooted();
        String schemaHint = SquadPlanDeserialiser.buildSchemaPrompt(planClass);
        AgentResponse response = submitTo(role, input + schemaHint);
        return SquadPlanDeserialiser.deserialise(response.content(), planClass);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public AgentMessageBus      getBus()     { ensureBooted(); return bus; }
    public AgentCircuitBreaker  getBreaker() { ensureBooted(); return breaker; }
    public AgentRegistry getRegistry() { ensureBooted(); return registry; }
    public SquadConfig   getConfig()   { return config; }
    public boolean       isBooted()    { return booted; }

    // ── Internals ─────────────────────────────────────────────────────

    private void ensureBooted() {
        if (!booted) {
            throw new IllegalStateException(
                "[SquadOS] SquadContext has not been booted. Call boot() first."
            );
        }
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
                w.getName(),
                w.getRole(),
                w.getOptions().temperature(),
                w.getOptions().maxTokens());
        }
        System.out.printf("%n  SquadContext ready — %d agent(s) online.%n%n",
            registry.count());
    }

    private static void log(String fmt, Object... args) {
        System.out.printf("[SquadOS] " + fmt + "%n", args);
    }
}
