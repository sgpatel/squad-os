package io.squados.annotation;

import java.lang.annotation.*;

/**
 * Externalise the agent's system prompt to a classpath file with {{variable}} substitution.
 *
 * path      — Classpath resource path (e.g. "prompts/analyst-prompt.txt")
 * variables — Comma-separated list of variable names injected at runtime
 *             (e.g. "agentName,role,missionProfile")
 *
 * Template syntax: {{variableName}} — replaced at boot time.
 *
 * Built-in variables always available:
 *   {{agentName}}   — agent's name
 *   {{role}}        — agent's role
 *   {{description}} — @Agent.description()
 *   {{profile}}     — active mission profile
 *
 * Usage:
 * <pre>
 *   @Agent(role = AgentRole.ANALYST)
 *   @PromptTemplate(path = "prompts/analyst-prompt.txt")
 *   public class AnalystAgent {}
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PromptTemplate {
    String   path();
    String[] variables() default {};
}
