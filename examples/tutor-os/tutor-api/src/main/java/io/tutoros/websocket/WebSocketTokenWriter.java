package io.tutoros.websocket;

import io.squados.llm.StreamToken;
import io.squados.llm.TokenWriter;

/**
 * WebSocketTokenWriter — bridges SquadOS @Streaming with TutorOS WebSocket clients.
 *
 * DirectTutorAgent is annotated with:
 *   @Streaming(writer = WebSocketTokenWriter.class, chunkSize = 3)
 *
 * SquadOS's StreamingEngine calls write(StreamToken) for each chunk produced by the
 * LLM. This implementation forwards tokens to TutoringWebSocketHandler, which
 * broadcasts them to all WebSocket clients connected to the current session.
 *
 * The sessionId is set on this writer before streaming begins via setSessionId().
 * SquadOS injects the writer as a Spring bean and the pipeline sets the context
 * per invocation.
 *
 * ThreadLocal is used so that concurrent sessions on different threads each have
 * their own sessionId without interfering.
 *
 * Stream lifecycle:
 *   - write(StreamToken) is called for each chunk
 *   - The final chunk has isLast()==true; we broadcast DONE then call flush()
 *   - flush() is the only post-stream hook on TokenWriter (no complete/error)
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
     * When token.isLast() is true, broadcasts a DONE frame in addition
     * to the final TOKEN frame. The ThreadLocal is cleared in flush().
     *
     * @param token a single token chunk from the LLM stream
     */
    @Override
    public void write(StreamToken token) {
        String sessionId = sessionIdHolder.get();
        if (sessionId == null) return;

        if (handler.hasActiveClients(sessionId)) {
            String text = token.text();
            if (text != null && !text.isEmpty()) {
                handler.broadcastToken(sessionId, text);
            }
            if (token.isLast()) {
                handler.broadcastDone(sessionId);
            }
        }
    }

    /**
     * Called by SquadOS StreamingEngine after the final token.
     * Cleans up the ThreadLocal so the bean is reusable across requests.
     */
    @Override
    public void flush() {
        sessionIdHolder.remove();
    }
}
