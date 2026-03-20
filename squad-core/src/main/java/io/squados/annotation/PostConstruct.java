package io.squados.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be called after the agent is instantiated
 * and its config is injected from squad.yml.
 *
 * Behaves identically to Jakarta's @PostConstruct —
 * defined here so SquadOS has zero dependency on Jakarta EE.
 *
 * Rules:
 * - Must be a no-arg void method
 * - Called exactly once per agent instance
 * - Called before any task is submitted to the agent
 *
 * Usage:
 * <pre>
 * {@literal @}Agent(role = AgentRole.SUPPORT)
 * public class NurseBotAgent {
 *     {@literal @}PostConstruct
 *     public void init() {
 *         System.out.println("NurseBot ready — heal channels open");
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostConstruct {
}
