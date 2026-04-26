package io.tutoros.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TutoringWebSocketHandler — bridges DirectTutorAgent's @Streaming output
 * to connected WebSocket clients.
 *
 * Flow:
 *   1. Client connects to ws://localhost:8080/ws/session/{sessionId}/stream
 *   2. Session ID is extracted from the URI path
 *   3. When SessionController processes a message and DirectTutorAgent streams
 *      tokens, each chunk is forwarded here via broadcastToken()
 *   4. Each WebSocket frame is a JSON StreamFrame:
 *        { "type": "TOKEN", "content": "hello" }
 *        { "type": "DONE",  "content": "" }
 *        { "type": "ERROR", "content": "reason" }
 *
 * Multiple clients can connect to the same session (e.g. student + parent view).
 * All connected clients receive the same token stream.
 *
 * Thread safety: sessions map is ConcurrentHashMap; send is synchronized per session.
 */
public class TutoringWebSocketHandler extends TextWebSocketHandler {

    /** sessionId → list of active WebSocket sessions */
    private final Map<String, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // ── WebSocket lifecycle ───────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        String sessionId = extractSessionId(wsSession);
        sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(wsSession.getId(), wsSession);

        sendFrame(wsSession, StreamFrame.connected(
            "Connected to session " + sessionId + ". Waiting for tutor response..."));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String sessionId = extractSessionId(wsSession);
        Map<String, WebSocketSession> sessionClients = sessions.get(sessionId);
        if (sessionClients != null) {
            sessionClients.remove(wsSession.getId());
            if (sessionClients.isEmpty()) {
                sessions.remove(sessionId);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable ex) {
        sendFrame(wsSession, StreamFrame.error("Transport error: " + ex.getMessage()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client-to-server messages on the stream channel are not currently used.
        // Chat messages go via POST /session/{id}/message (HTTP).
        // Reserved for future: heartbeat PING / client-side abort.
        if ("PING".equals(message.getPayload())) {
            sendFrame(session, StreamFrame.pong());
        }
    }

    // ── Public API (called by DirectTutorAgent's WebSocketTokenWriter) ───────

    /**
     * Broadcast a single token to all clients connected to sessionId.
     * Called by {@code WebSocketTokenWriter} which wraps this handler and is
     * passed to DirectTutorAgent via @Streaming(writer=WebSocketTokenWriter.class).
     *
     * @param sessionId the TutorOS session ID (learnerId:subject)
     * @param token     a single streaming token from the LLM
     */
    public void broadcastToken(String sessionId, String token) {
        broadcast(sessionId, StreamFrame.token(token));
    }

    /**
     * Signal stream completion to all clients.
     * @param sessionId the TutorOS session ID
     */
    public void broadcastDone(String sessionId) {
        broadcast(sessionId, StreamFrame.done());
    }

    /**
     * Signal a stream error to all clients.
     * @param sessionId the TutorOS session ID
     * @param reason    human-readable error message
     */
    public void broadcastError(String sessionId, String reason) {
        broadcast(sessionId, StreamFrame.error(reason));
    }

    // ── Pipeline-event broadcast helpers (called by WebSocketPipelineEventBus) ──

    /** Notify clients that pipeline step {@code stage} has started. */
    public void broadcastStageStart(String sessionId, String stage) {
        broadcast(sessionId, new StreamFrame("STAGE_START", stage, "", null));
    }

    /** Notify clients that pipeline step {@code stage} has completed. */
    public void broadcastStageDone(String sessionId, String stage, Object payload) {
        broadcast(sessionId, new StreamFrame("STAGE_DONE", stage, "", payload));
    }

    /** Notify clients that pipeline step {@code stage} failed. */
    public void broadcastStageError(String sessionId, String stage, String reason) {
        broadcast(sessionId, new StreamFrame("STAGE_ERROR", stage, reason, null));
    }

    /** Stream a single resolved debate round to clients (for the debate panel). */
    public void broadcastDebateRound(String sessionId, Object round) {
        broadcast(sessionId, new StreamFrame("DEBATE_ROUND", null, "", round));
    }

    /**
     * Publish the final tutor message in structured form. Useful even when
     * tokens have streamed — gives the UI a single source of truth for
     * citations / confidence / debate / teachingStyle.
     */
    public void broadcastMessage(String sessionId, Object messagePayload) {
        broadcast(sessionId, new StreamFrame("MESSAGE", null, "", messagePayload));
    }

    /**
     * Publish a renderer-ready VisualAsset produced by VisualisationAgent.
     * The FE FrameTranslator attaches it to the pending tutor message so
     * the matching renderer (chem / plot / …) mounts inside the chat bubble.
     */
    public void broadcastVisual(String sessionId, Object visualAsset) {
        broadcast(sessionId, new StreamFrame("VISUAL", null, "", visualAsset));
    }

    /** Publish a mastery change so the progress UI can react in real time. */
    public void broadcastMasteryDelta(String sessionId, String concept,
                                       double before, double after) {
        broadcast(sessionId, new StreamFrame("MASTERY_DELTA", null, "",
            java.util.Map.of("concept", concept, "before", before, "after", after)));
    }

    /**
     * Check whether any client is currently connected to the given session.
     * DirectTutorAgent can skip streaming overhead when no client is connected.
     *
     * @param sessionId the TutorOS session ID
     * @return true if at least one WebSocket client is connected
     */
    public boolean hasActiveClients(String sessionId) {
        Map<String, WebSocketSession> clients = sessions.get(sessionId);
        return clients != null && !clients.isEmpty();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void broadcast(String sessionId, StreamFrame frame) {
        Map<String, WebSocketSession> clients = sessions.get(sessionId);
        if (clients == null || clients.isEmpty()) return;

        clients.values().forEach(ws -> sendFrame(ws, frame));
    }

    private void sendFrame(WebSocketSession ws, StreamFrame frame) {
        if (!ws.isOpen()) return;
        try {
            String json = mapper.writeValueAsString(frame);
            synchronized (ws) {
                ws.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            // Client disconnected mid-stream — not an error
        }
    }

    /**
     * Extract the session ID from the WebSocket URI.
     * URI format: /ws/session/{sessionId}/stream
     * Example:    /ws/session/alice:mathematics/stream → "alice:mathematics"
     */
    private String extractSessionId(WebSocketSession ws) {
        String path = ws.getUri() != null ? ws.getUri().getPath() : "";
        // path = /ws/session/{sessionId}/stream
        String[] parts = path.split("/");
        // parts[0]="" [1]="ws" [2]="session" [3]="{sessionId}" [4]="stream"
        return parts.length >= 4 ? parts[3] : "unknown";
    }

    // ── Frame type ────────────────────────────────────────────────────────────

    /**
     * JSON frame sent to WebSocket clients.
     *
     * Wire shape: {@code { type, stage?, content?, payload? }}.
     *
     * Supported {@code type} values:
     *
     *   CONNECTED      — handshake confirmation
     *   TOKEN          — single LLM token chunk (during the tutor stage)
     *   DONE           — pipeline run complete
     *   ERROR          — transport / pipeline error
     *   PONG           — heartbeat reply
     *   STAGE_START    — pipeline entered a step (use {@link #stage()})
     *   STAGE_DONE     — pipeline finished a step ({@link #payload()} carries
     *                    a stage-specific result, e.g. content snippet,
     *                    debate winner, mastery delta)
     *   STAGE_ERROR    — pipeline step failed ({@link #content()} carries the reason)
     *   DEBATE_ROUND   — one resolved debate round ({@link #payload()})
     *   MESSAGE        — final structured tutor message ({@link #payload()}
     *                    carries body / teachingStyle / citations / confidence)
     *   MASTERY_DELTA  — concept mastery changed ({@link #payload()})
     *   VISUAL         — renderer-ready VisualAsset ({@link #payload()} carries
     *                    {type, concept, title, caption, specJson, altText})
     *
     * The legacy two-arg constructor is preserved so older callers keep
     * compiling; {@code stage} and {@code payload} default to {@code null}.
     */
    public record StreamFrame(String type, String stage, String content, Object payload) {

        /** Legacy constructor used by older callers / older tests. */
        public StreamFrame(String type, String content) {
            this(type, null, content, null);
        }

        // ── Static factories for the common cases ────────────────────────
        public static StreamFrame connected(String msg)         { return new StreamFrame("CONNECTED", null, msg, null); }
        public static StreamFrame token(String chunk)           { return new StreamFrame("TOKEN",     null, chunk, null); }
        public static StreamFrame done()                        { return new StreamFrame("DONE",      null, "", null); }
        public static StreamFrame error(String reason)          { return new StreamFrame("ERROR",     null, reason, null); }
        public static StreamFrame pong()                        { return new StreamFrame("PONG",      null, "", null); }
    }
}
