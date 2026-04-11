package io.squados.llm;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic, zero-cost LlmPort for unit and integration tests.
 *
 * Usage:
 * <pre>
 *   MockLlmPort mock = new MockLlmPort();
 *   mock.setResponse("Plan an attack", "Roger. Flanking north gate.");
 *   AgentWrapper wrapper = new AgentWrapper(agent, config, mock);
 *   AgentResponse r = wrapper.execute(new TaskContext("Plan an attack"));
 *   assertEquals("Roger. Flanking north gate.", r.content());
 * </pre>
 *
 * All calls are recorded so tests can assert call counts and payloads.
 */
public class MockLlmPort implements LlmPort {

    /** Fixed responses keyed by substring match on userMessage */
    private final Map<String, String> responses = new HashMap<>();

    /** Default response if no key matches */
    private String defaultResponse = "Mock LLM response — configure via setResponse()";

    /** Records every call for assertion in tests */
    private final java.util.List<CallRecord> calls = new java.util.ArrayList<>();

    /** Call counter */
    private final AtomicInteger callCount = new AtomicInteger(0);

    // ── Configuration ────────────────────────────────────────────────

    /**
     * Register a fixed response for when userMessage contains the given key.
     */
    public MockLlmPort setResponse(String userMessageContains, String response) {
        responses.put(userMessageContains, response);
        return this;
    }

    /**
     * Set the fallback response when no key matches.
     */
    public MockLlmPort setDefaultResponse(String response) {
        this.defaultResponse = response;
        return this;
    }

    // ── LlmPort implementation ────────────────────────────────────────

    @Override
    public LlmResponse chat(String systemPrompt, String userMessage, LlmOptions options) {
        callCount.incrementAndGet();
        String content = resolveResponse(userMessage);
        calls.add(new CallRecord(systemPrompt, userMessage, options, content));
        return new LlmResponse(content, userMessage.length(), content.length(), "mock-model");
    }

    @Override
    public void chatStream(String systemPrompt, String userMessage,
                           LlmOptions options, TokenWriter writer) {
        String content = resolveResponse(userMessage);
        callCount.incrementAndGet();
        calls.add(new CallRecord(systemPrompt, userMessage, options, content));
        // Split into chunks to simulate streaming
        int chunkSize = Math.max(1, content.length() / 3);
        for (int i = 0; i < content.length(); i += chunkSize) {
            String chunk = content.substring(i, Math.min(i + chunkSize, content.length()));
            boolean last = (i + chunkSize) >= content.length();
            writer.write(last ? StreamToken.last(chunk) : StreamToken.of(chunk));
        }
        if (content.isEmpty()) writer.write(StreamToken.last());
        writer.flush();
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userMessage,
                                Class<T> responseType, LlmOptions options) {
        // For tests — return null. Override in test subclass for typed results.
        callCount.incrementAndGet();
        calls.add(new CallRecord(systemPrompt, userMessage, options, "[structured]"));
        return null;
    }

    // ── Assertion helpers ─────────────────────────────────────────────

    public int getCallCount()               { return callCount.get(); }
    public boolean wasCalled()              { return callCount.get() > 0; }
    public java.util.List<CallRecord> getCalls() { return java.util.Collections.unmodifiableList(calls); }
    public CallRecord getLastCall()         { return calls.isEmpty() ? null : calls.get(calls.size() - 1); }

    public void reset() {
        calls.clear();
        callCount.set(0);
    }

    // ── Internals ─────────────────────────────────────────────────────

    private String resolveResponse(String userMessage) {
        for (var entry : responses.entrySet()) {
            if (userMessage.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return defaultResponse;
    }

    /** Immutable record of a single call to the mock */
    public record CallRecord(
            String systemPrompt,
            String userMessage,
            LlmOptions options,
            String responseContent
    ) {}
}
