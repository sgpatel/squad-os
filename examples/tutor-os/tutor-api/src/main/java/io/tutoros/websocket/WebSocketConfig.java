package io.tutoros.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket configuration for TutorOS streaming.
 *
 * Registers the TutoringWebSocketHandler at:
 *   ws://localhost:8080/ws/session/{sessionId}/stream
 *
 * The frontend connects to this endpoint after calling POST /session/{id}/message.
 * The DirectTutorAgent's @Streaming annotation writes tokens to a WebSocket-backed
 * TokenWriter, which forwards each chunk to the connected client.
 *
 * CORS is allowed from any origin for local development.
 * In production, replace setAllowedOrigins("*") with your domain.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TutoringWebSocketHandler handler;

    public WebSocketConfig(TutoringWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(handler, "/ws/session/*/stream")
            .setAllowedOrigins("*");   // replace with specific origin in production
    }
}
