package io.squados.conversation;

import io.squados.llm.ConversationMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, heap-backed conversation store.
 * Suitable for single-JVM deployments and tests.
 * Does not survive JVM restarts.
 */
public class InProcessConversationStore implements ConversationStore {

    private final Map<String, List<ConversationMessage>> store = new ConcurrentHashMap<>();

    @Override
    public void append(String sessionId, ConversationMessage message) {
        store.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
             .add(message);
    }

    @Override
    public List<ConversationMessage> getHistory(String sessionId) {
        List<ConversationMessage> hist = store.get(sessionId);
        if (hist == null) return List.of();
        synchronized (hist) {
            return List.copyOf(hist);
        }
    }

    @Override
    public void trim(String sessionId, int maxTurns) {
        List<ConversationMessage> hist = store.get(sessionId);
        if (hist == null) return;
        synchronized (hist) {
            // Each turn = 2 messages (user + assistant); keep the last maxTurns*2
            int maxMessages = maxTurns * 2;
            while (hist.size() > maxMessages) {
                hist.remove(0);
            }
        }
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
    }

    @Override
    public int size(String sessionId) {
        List<ConversationMessage> hist = store.get(sessionId);
        return hist == null ? 0 : hist.size();
    }
}
