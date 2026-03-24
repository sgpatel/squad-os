package io.sentinel.backend.websocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
@Component
public class MentionWebSocketHandler extends TextWebSocketHandler {
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Override public void afterConnectionEstablished(WebSocketSession session) { sessions.add(session); }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus s) { sessions.remove(session); }
    public void broadcast(String type, Object data) {
        try {
            String json = mapper.writeValueAsString(new Event(type, data));
            TextMessage msg = new TextMessage(json);
            sessions.removeIf(s -> !s.isOpen());
            for (WebSocketSession s : sessions) {
                try { s.sendMessage(msg); } catch (Exception ignored) {}
            }
        } catch (Exception e) { System.err.println("WS broadcast error: " + e.getMessage()); }
    }
    public record Event(String type, Object data) {}
}