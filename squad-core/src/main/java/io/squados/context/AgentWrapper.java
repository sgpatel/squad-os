package io.squados.context;

import io.squados.agent.AgentResponse;
import io.squados.agent.TaskContext;
import io.squados.annotation.Agent;
import io.squados.annotation.AgentRole;
import io.squados.annotation.PostConstruct;
import io.squados.config.SquadConfig;
import io.squados.health.AgentCircuitBreaker;
import io.squados.llm.LlmOptions;
import io.squados.llm.LlmPort;
import io.squados.llm.LlmResponse;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * Wraps a single @Agent-annotated class instance.
 *
 * Responsibilities:
 *   1. Instantiate the agent class via reflection
 *   2. Resolve effective LlmOptions (yml overrides &gt; role defaults)
 *   3. Build the system prompt from @Agent metadata
 *   4. Call LlmPort.chat() and wrap the result in AgentResponse
 *   5. Invoke @PostConstruct after instantiation
 *
 * This is the execution heart of Phase 1 — everything else
 * exists to get a task to this class and get a response back.
 */
public class AgentWrapper {

    private final Object             instance;
    private final Class<?>           agentClass;
    private final Agent              annotation;
    private final AgentRole          role;
    private final String             name;
    private final LlmOptions         options;
    private final LlmPort            llm;
    private AgentCircuitBreaker breaker;

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

        // Resolve effective LlmOptions: yml overrides &gt; role defaults
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
     * Called by SquadContext after all agents are registered.
     * Safe to call multiple times — only fires if method is present.
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
                    return; // Only the first @PostConstruct fires
                } catch (Exception e) {
                    throw new RuntimeException(
                        "[SquadOS] @PostConstruct failed in agent '"
                        + name + "': " + e.getCause().getMessage(),
                        e.getCause()
                    );
                }
            }
        }
        // No @PostConstruct — fine, not required
    }

    // ── Task execution ────────────────────────────────────────────────

    /**
     * Execute a task using this agent.
     *
     * Builds the system prompt from @Agent metadata, calls LlmPort,
     * and wraps the result in a typed AgentResponse.
     */
    public AgentResponse execute(TaskContext ctx) {
        Instant start = Instant.now();
        if (breaker != null && !breaker.allowCall(role)) {
            return AgentResponse.failure(role, name,
                "Circuit open — " + name + " unavailable.", start);
        }
        try {
            String systemPrompt = buildSystemPrompt(ctx);
            String userMessage  = ctx.getTaskDescription();
            LlmResponse raw = llm.chat(systemPrompt, userMessage, options);
            AgentResponse response = AgentResponse.of(raw, role, name, start);
            if (breaker != null) breaker.onSuccess(role, response.latency().toMillis());
            return response;
        } catch (Exception e) {
            if (breaker != null) breaker.onFailure(role, e.getMessage());
            return AgentResponse.failure(role, name,
                "Execution failed: " + e.getMessage(), start);
        }
    }

    // ── System prompt ─────────────────────────────────────────────────

    /**
     * Build the system prompt injected before every LLM call.
     *
     * Format:
     *   You are {name}, a {role} agent in the SquadOS framework.
     *   {description if present}
     *   Profile: {profile if set}
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

        // Append profile context if set and non-default
        if (!annotation.profile().isBlank()
                && !annotation.profile().equals("default")) {
            sb.append("\nActive profile: ").append(annotation.profile()).append(".");
        }

        sb.append("\nTask ID: ").append(ctx.getTaskId()).append(".");

        return sb.toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public void       setBreaker(AgentCircuitBreaker b) { this.breaker = b; }
    public AgentRole  getRole()       { return role; }
    public String     getName()       { return name; }
    public LlmOptions getOptions()    { return options; }
    public Object     getInstance()   { return instance; }
    public Class<?>   getAgentClass() { return agentClass; }

    // ── Helpers ───────────────────────────────────────────────────────

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
