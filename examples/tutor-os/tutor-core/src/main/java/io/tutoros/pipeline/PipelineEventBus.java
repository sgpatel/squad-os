package io.tutoros.pipeline;

/**
 * PipelineEventBus — transport-agnostic seam for streaming pipeline progress
 * to interested observers (WebSocket clients, OpenTelemetry exporters, audit
 * logs, etc.).
 *
 * Why an interface (rather than calling the WebSocketHandler directly)?
 *
 *   - tutor-core has no dependency on Spring, Servlet, or WebSocket APIs.
 *     Keeping that boundary clean lets the pipeline be exercised from unit
 *     tests, CLI runners, or alternate transports without dragging in a
 *     Spring context.
 *   - Multiple observers can be composed (logging + WS + tracing) without
 *     the pipeline knowing.
 *   - In tests, {@link #NOOP} is the natural default — assertions can be
 *     made by injecting a recording implementation.
 *
 * Wire protocol (as encoded by tutor-api's WebSocketPipelineEventBus):
 *
 *   stageStart   → frame {type:"STAGE_START",  stage:&lt;key&gt;}
 *   stageDone    → frame {type:"STAGE_DONE",   stage:&lt;key&gt;, payload:&lt;output&gt;}
 *   stageError   → frame {type:"STAGE_ERROR",  stage:&lt;key&gt;, content:&lt;reason&gt;}
 *   debateRound  → frame {type:"DEBATE_ROUND", payload:&lt;round&gt;}
 *   message      → frame {type:"MESSAGE",      payload:&lt;tutor message&gt;}
 *   masteryDelta → frame {type:"MASTERY_DELTA", payload:{concept,before,after}}
 *   done         → frame {type:"DONE"}
 *
 * Stage keys mirror the UI's PipelineStageKey union:
 *   guardian, diagnostic, planner, content, debate, tutor,
 *   practice, assessment, progress
 */
public interface PipelineEventBus {

    void stageStart(String sessionId, String stage);

    void stageDone(String sessionId, String stage, Object payload);

    void stageError(String sessionId, String stage, String reason);

    void debateRound(String sessionId, Object round);

    void message(String sessionId, Object messagePayload);

    void masteryDelta(String sessionId, String concept, double before, double after);

    void done(String sessionId);

    /**
     * No-op implementation — used as the default in tests and when no
     * transport bus has been wired. Keeps TutoringPipeline runnable in
     * isolation.
     */
    PipelineEventBus NOOP = new PipelineEventBus() {
        @Override public void stageStart(String sessionId, String stage) {}
        @Override public void stageDone(String sessionId, String stage, Object payload) {}
        @Override public void stageError(String sessionId, String stage, String reason) {}
        @Override public void debateRound(String sessionId, Object round) {}
        @Override public void message(String sessionId, Object messagePayload) {}
        @Override public void masteryDelta(String sessionId, String concept, double before, double after) {}
        @Override public void done(String sessionId) {}
    };
}
