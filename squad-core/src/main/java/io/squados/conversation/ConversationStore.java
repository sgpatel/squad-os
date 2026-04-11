package io.squados.conversation;

import io.squados.llm.ConversationMessage;
import java.util.List;

/**
 * Persists multi-turn conversation histories keyed by sessionId.
 */
public interface ConversationStore {

    /** Append a message to the conversation identified by sessionId. */
    void append(String sessionId, ConversationMessage message);

    /** Retrieve all messages for a session, oldest first. */
    List<ConversationMessage> getHistory(String sessionId);

    /** Trim the conversation to at most maxTurns turns (one turn = user + assistant). */
    void trim(String sessionId, int maxTurns);

    /** Delete all messages for a session. */
    void clear(String sessionId);

    /** Count messages in a session. */
    int size(String sessionId);
}
