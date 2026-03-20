package io.squados.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a SquadOS managed agent.
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.STRATEGIST, name = "Oracle")
 * public class OracleAgent {
 *     {@literal @}PostConstruct
 *     public void init() { ... }
 * }
 * </pre>
 *
 * The SquadContext scans for this annotation at boot,
 * instantiates the class, injects config from squad.yml,
 * and registers it in the AgentRegistry.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Agent {

    /** The role this agent fulfils. Required. */
    AgentRole role();

    /**
     * Display name. Defaults to the simple class name if empty.
     * Injected into the agent's system prompt automatically.
     */
    String name() default "";

    /**
     * Mission profile this agent is active in.
     * Matches squad.yml profile field.
     * Empty string = active in all profiles.
     */
    String profile() default "";

    /**
     * Human-readable description of this agent's purpose.
     * Appended to the system prompt so the LLM understands its role.
     */
    String description() default "";
}
