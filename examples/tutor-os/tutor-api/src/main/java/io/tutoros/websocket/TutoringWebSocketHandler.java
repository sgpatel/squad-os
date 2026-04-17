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

        sendFrame(wsSession, new StreamFrame("CONNECTED",
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
        sendFrame(wsSession, new StreamFrame("ERROR", "Transport error: " + ex.getMessage()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client-to-server messages on the stream channel are not currently used.
        // Chat messages go via POST /session/{id}/message (HTTP).
        // Reserved for future: heartbeat PING / client-side abort.
        if ("PING".equals(message.getPayload())) {
            sendFrame(session, new StreamFrame("PONG", ""));
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
        broadcast(sessionId, new StreamFrame("TOKEN", token));
    }

    /**
     * Signal stream completion to all clients.
     * @param sessionId the TutorOS session ID
     */
    public void broadcastDone(String sessionId) {
        broadcast(sessionId, new StreamFrame("DONE", ""));
    }

    /**
     * Signal a stream error to all clients.
     * @param sessionId the TutorOS session ID
     * @param reason    human-readable error message
     */
    public void broadcastError(String sessionId, String reason) {
        broadcast(sessionId, new StreamFrame("ERROR", reason));
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
     * type: CONNECTED | TOKEN | DONE | ERROR | PONG
     */
    public record StreamFrame(String type, String content) {}
}
