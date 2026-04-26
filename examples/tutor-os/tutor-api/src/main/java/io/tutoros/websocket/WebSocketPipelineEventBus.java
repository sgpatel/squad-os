package io.tutoros.websocket;

import io.tutoros.pipeline.PipelineEventBus;

/**
 * WebSocketPipelineEventBus — bridges {@link PipelineEventBus} (called by
 * {@link io.tutoros.pipeline.TutoringPipeline}) onto the WebSocket frames
 * broadcast by {@link TutoringWebSocketHandler}.
 *
 * Lifetime: a single Spring bean wired in {@code TutorBeansConfig} and
 * passed to the pipeline constructor. All methods are no-ops if the
 * sessionId is null/blank — defensive against tests that call the pipeline
 * without a real session attached.
 *
 * The bridge is intentionally thin — the wire encoding (event type names,
 * field shapes) lives in {@link TutoringWebSocketHandler.StreamFrame} so
 * that the pipeline core has no compile-time coupling to JSON schema.
 */
public class WebSocketPipelineEventBus implements PipelineEventBus {

    private final TutoringWebSocketHandler handler;

    public WebSocketPipelineEventBus(TutoringWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void stageStart(String sessionId, String stage) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastStageStart(sessionId, stage);
    }

    @Override
    public void stageDone(String sessionId, String stage, Object payload) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastStageDone(sessionId, stage, payload);
    }

    @Override
    public void stageError(String sessionId, String stage, String reason) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastStageError(sessionId, stage, reason);
    }

    @Override
    public void debateRound(String sessionId, Object round) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastDebateRound(sessionId, round);
    }

    @Override
    public void message(String sessionId, Object messagePayload) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastMessage(sessionId, messagePayload);
    }

    @Override
    public void masteryDelta(String sessionId, String concept, double before, double after) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastMasteryDelta(sessionId, concept, before, after);
    }

    @Override
    public void visual(String sessionId, Object visualAsset) {
        if (sessionId == null || sessionId.isBlank() || visualAsset == null) return;
        handler.broadcastVisual(sessionId, visualAsset);
    }

    @Override
    public void done(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        handler.broadcastDone(sessionId);
    }
}
