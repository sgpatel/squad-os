package io.squados.examples.tutoros.websocket;

import io.squados.streaming.TokenWriter;

/**
 * WebSocketTokenWriter — bridges SquadOS @Streaming with TutorOS WebSocket clients.
 *
 * DirectTutorAgent is annotated with:
 *   @Streaming(writer = WebSocketTokenWriter.class, chunkSize = 3)
 *
 * SquadOS's StreamingEngine calls write(token) for each chunk produced by the LLM.
 * This implementation forwards tokens to TutoringWebSocketHandler, which broadcasts
 * them to all WebSocket clients connected to the current session.
 *
 * The sessionId is set on this writer before streaming begins via setSessionId().
 * SquadOS injects the writer as a Spring bean and the pipeline sets the context
 * per invocation.
 *
 * ThreadLocal is used so that concurrent sessions on different threads each have
 * their own sessionId without interfering.
 */
public class WebSocketTokenWriter implements TokenWriter {

    private final TutoringWebSocketHandler handler;

    /** Holds the active sessionId for the current streaming call. */
    private final ThreadLocal<String> sessionIdHolder = new ThreadLocal<>();

    public WebSocketTokenWriter(TutoringWebSocketHandler handler) {
        this.handler = handler;
    }

    /**
     * Set the session ID before streaming begins.
     * Called by TutoringPipeline before invoking DirectTutorAgent.
     *
     * @param sessionId the TutorOS session ID (learnerId:subject)
     */
    public void setSessionId(String sessionId) {
        sessionIdHolder.set(sessionId);
    }

    /**
     * Called by SquadOS StreamingEngine for each token chunk.
     * Forwards to all WebSocket clients connected to the session.
     *
     * If no clients are connected, the token is silently discarded —
     * the full response is still assembled in-memory and returned via
     * the HTTP response (SessionController handles both paths).
     *
     * @param token a single token or small chunk from the LLM stream
     */
    @Override
    public void write(String token) {
        String sessionId = sessionIdHolder.get();
        if (sessionId != null && handler.hasActiveClients(sessionId)) {
            handler.broadcastToken(sessionId, token);
        }
    }

    /**
     * Called by SquadOS StreamingEngine when the LLM stream ends normally.
     */
    @Override
    public void complete() {
        String sessionId = sessionIdHolder.get();
        if (sessionId != null) {
            handler.broadcastDone(sessionId);
        }
        sessionIdHolder.remove();   // clean up ThreadLocal
    }

    /**
     * Called by SquadOS StreamingEngine if the LLM stream errors.
     *
     * @param error the exception that terminated the stream
     */
    @Override
    public void error(Throwable error) {
        String sessionId = sessionIdHolder.get();
        if (sessionId != null) {
            handler.broadcastError(sessionId, error.getMessage());
        }
        sessionIdHolder.remove();
    }
}
