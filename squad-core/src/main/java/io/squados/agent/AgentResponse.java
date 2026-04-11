package io.squados.agent;

import io.squados.annotation.AgentRole;
import io.squados.llm.LlmResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * The result of an agent executing a task.
 *
 * Wraps the LLM response content alongside the agent identity,
 * timing metadata, and token usage — everything needed for
 * the observability layer (Phase 4) to report cost and latency.
 */
public class AgentResponse {

    private final String    content;
    private final AgentRole role;
    private final String    agentName;
    private final int       promptTokens;
    private final int       completionTokens;
    private final Duration  latency;
    private final boolean   success;
    private final boolean   skipped;
    private final String    errorMessage;

    // ── Constructors ──────────────────────────────────────────────────

    /** Successful response */
    private AgentResponse(String content, AgentRole role, String agentName,
                          int promptTokens, int completionTokens, Duration latency) {
        this.content          = content;
        this.role             = role;
        this.agentName        = agentName;
        this.promptTokens     = promptTokens;
        this.completionTokens = completionTokens;
        this.latency          = latency;
        this.success          = true;
        this.skipped          = false;
        this.errorMessage     = null;
    }

    /** Failed response */
    private AgentResponse(AgentRole role, String agentName,
                          String errorMessage, Duration latency) {
        this.content          = null;
        this.role             = role;
        this.agentName        = agentName;
        this.promptTokens     = 0;
        this.completionTokens = 0;
        this.latency          = latency;
        this.success          = false;
        this.skipped          = false;
        this.errorMessage     = errorMessage;
    }

    /** Skipped response — @Condition or @Step condition evaluated to false */
    private AgentResponse(AgentRole role, String agentName, boolean skipped) {
        this.content          = null;
        this.role             = role;
        this.agentName        = agentName;
        this.promptTokens     = 0;
        this.completionTokens = 0;
        this.latency          = Duration.ZERO;
        this.success          = false;
        this.skipped          = true;
        this.errorMessage     = null;
    }

    // ── Factory methods ───────────────────────────────────────────────

    public static AgentResponse of(LlmResponse llmResponse, AgentRole role,
                                   String agentName, Instant startedAt) {
        return new AgentResponse(
            llmResponse.content(),
            role,
            agentName,
            llmResponse.promptTokens(),
            llmResponse.completionTokens(),
            Duration.between(startedAt, Instant.now())
        );
    }

    public static AgentResponse failure(AgentRole role, String agentName,
                                        String errorMessage, Instant startedAt) {
        return new AgentResponse(
            role, agentName, errorMessage,
            Duration.between(startedAt, Instant.now())
        );
    }

    /** Pre-computed success — used by DurableEngine returning cached results. */
    public static AgentResponse success(AgentRole role, String agentName, String content) {
        return new AgentResponse(content, role, agentName, 0, 0, Duration.ZERO);
    }

    /** Skipped — @Condition evaluated to false; no LLM call was made. */
    public static AgentResponse skipped(AgentRole role, String agentName) {
        return new AgentResponse(role, agentName, true);
    }

    // ── Getters ───────────────────────────────────────────────────────

    public String    content()          { return content; }
    public AgentRole role()             { return role; }
    public String    agentName()        { return agentName; }
    public int       promptTokens()     { return promptTokens; }
    public int       completionTokens() { return completionTokens; }
    public int       totalTokens()      { return promptTokens + completionTokens; }
    public Duration  latency()          { return latency; }
    public boolean   isSuccess()        { return success; }
    public boolean   isSkipped()        { return skipped; }
    public String    errorMessage()     { return errorMessage; }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }

    @Override
    public String toString() {
        if (!success) {
            return "AgentResponse{FAILED, agent='" + agentName
                    + "', error='" + errorMessage + "'}";
        }
        return "AgentResponse{agent='" + agentName
                + "', role=" + role
                + ", tokens=" + totalTokens()
                + ", latency=" + latency.toMillis() + "ms"
                + ", content='" + (content != null
                    ? content.substring(0, Math.min(80, content.length()))
                    : "null") + "...'}";
    }
}
