package io.squados.trace;

import io.squados.annotation.AgentRole;
import java.time.Instant;
import java.util.*;

/**
 * An immutable span recording one agent method call.
 * Compatible with OpenTelemetry span semantics.
 */
public class AgentSpan {

    public enum Status { OK, ERROR }

    private final String     traceId;
    private final String     spanId;
    private final String     spanName;
    private final AgentRole  agentRole;
    private final String     agentName;
    private final Instant    startTime;
    private final Instant    endTime;
    private final long       durationMs;
    private final Status     status;
    private final String     errorMessage;
    private final int        promptTokens;
    private final int        completionTokens;
    private final int        totalTokens;
    private final int        inputLength;
    private final int        outputLength;
    private final Map<String,String> attributes;

    private AgentSpan(Builder b) {
        this.traceId          = b.traceId;
        this.spanId           = b.spanId;
        this.spanName         = b.spanName;
        this.agentRole        = b.agentRole;
        this.agentName        = b.agentName;
        this.startTime        = b.startTime;
        this.endTime          = b.endTime;
        this.durationMs       = b.durationMs;
        this.status           = b.status;
        this.errorMessage     = b.errorMessage;
        this.promptTokens     = b.promptTokens;
        this.completionTokens = b.completionTokens;
        this.totalTokens      = b.promptTokens + b.completionTokens;
        this.inputLength      = b.inputLength;
        this.outputLength     = b.outputLength;
        this.attributes       = Collections.unmodifiableMap(b.attributes);
    }

    public String              getTraceId()          { return traceId; }
    public String              getSpanId()           { return spanId; }
    public String              getSpanName()         { return spanName; }
    public AgentRole           getAgentRole()        { return agentRole; }
    public String              getAgentName()        { return agentName; }
    public Instant             getStartTime()        { return startTime; }
    public Instant             getEndTime()          { return endTime; }
    public long                getDurationMs()       { return durationMs; }
    public Status              getStatus()           { return status; }
    public String              getErrorMessage()     { return errorMessage; }
    public int                 getPromptTokens()     { return promptTokens; }
    public int                 getCompletionTokens() { return completionTokens; }
    public int                 getTotalTokens()      { return totalTokens; }
    public int                 getInputLength()      { return inputLength; }
    public int                 getOutputLength()     { return outputLength; }
    public Map<String,String>  getAttributes()       { return attributes; }
    public boolean             isSuccess()           { return status == Status.OK; }
    public boolean             isError()             { return status == Status.ERROR; }

    @Override
    public String toString() {
        return String.format(
            "AgentSpan{name=%s, role=%s, duration=%dms, status=%s, tokens=%d}",
            spanName, agentRole, durationMs, status, totalTokens);
    }

    // ── Builder ──────────────────────────────────────────────────
    public static Builder builder(String spanName) {
        return new Builder(spanName);
    }

    public static class Builder {
        String traceId = UUID.randomUUID().toString().replace("-","").substring(0,16);
        String spanId  = UUID.randomUUID().toString().replace("-","").substring(0,8);
        String     spanName;
        AgentRole  agentRole   = AgentRole.WILDCARD;
        String     agentName   = "unknown";
        Instant    startTime   = Instant.now();
        Instant    endTime     = Instant.now();
        long       durationMs  = 0;
        Status     status      = Status.OK;
        String     errorMessage;
        int        promptTokens, completionTokens;
        int        inputLength, outputLength;
        Map<String,String> attributes = new LinkedHashMap<>();

        private Builder(String spanName) { this.spanName = spanName; }

        public Builder traceId(String v)          { traceId = v; return this; }
        public Builder agentRole(AgentRole v)     { agentRole = v; return this; }
        public Builder agentName(String v)        { agentName = v; return this; }
        public Builder startTime(Instant v)       { startTime = v; return this; }
        public Builder endTime(Instant v)         { endTime = v; return this; }
        public Builder durationMs(long v)         { durationMs = v; return this; }
        public Builder status(Status v)           { status = v; return this; }
        public Builder errorMessage(String v)     { errorMessage = v; return this; }
        public Builder promptTokens(int v)        { promptTokens = v; return this; }
        public Builder completionTokens(int v)    { completionTokens = v; return this; }
        public Builder inputLength(int v)         { inputLength = v; return this; }
        public Builder outputLength(int v)        { outputLength = v; return this; }
        public Builder attribute(String k, String v) { attributes.put(k, v); return this; }
        public AgentSpan build()                  { return new AgentSpan(this); }
    }
}