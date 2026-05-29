package io.squados.test;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Captures every AgentResponse produced during a test run.
 *
 * {@link AgentTestHarness} automatically feeds all responses through the
 * captor so tests can inspect what happened without instrumenting the agents.
 *
 * <pre>
 *   harness.submit("research AI");
 *   harness.submitTo(AgentRole.WRITER, "write blog post");
 *
 *   ResponseCaptor cap = harness.captor();
 *   assertEquals(2, cap.count());
 *   cap.assertAllSucceeded();
 *   assertTrue(cap.anyContentContains("AI"));
 *   cap.forRole(AgentRole.WRITER).forEach(r -> SquadAssertions.assertSuccess(r));
 * </pre>
 */
public class ResponseCaptor {

    private final List<AgentResponse> captured = new ArrayList<>();

    // ── Recording ─────────────────────────────────────────────────────

    void capture(AgentResponse r) {
        if (r != null) captured.add(r);
    }

    void clear() {
        captured.clear();
    }

    // ── Query ─────────────────────────────────────────────────────────

    /** All responses captured so far (in call order). */
    public List<AgentResponse> all() {
        return Collections.unmodifiableList(captured);
    }

    /** Number of responses captured. */
    public int count() { return captured.size(); }

    /** True if at least one response was captured. */
    public boolean hasCaptured() { return !captured.isEmpty(); }

    /** The most recent response, or empty if none captured. */
    public Optional<AgentResponse> last() {
        return captured.isEmpty() ? Optional.empty()
                                  : Optional.of(captured.get(captured.size() - 1));
    }

    /** All responses from the given role. */
    public List<AgentResponse> forRole(AgentRole role) {
        return captured.stream()
                .filter(r -> r.role() == role)
                .collect(Collectors.toList());
    }

    /** All responses from the named agent. */
    public List<AgentResponse> forAgent(String agentName) {
        return captured.stream()
                .filter(r -> agentName.equals(r.agentName()))
                .collect(Collectors.toList());
    }

    /** All successful responses. */
    public List<AgentResponse> successes() {
        return captured.stream().filter(AgentResponse::isSuccess).collect(Collectors.toList());
    }

    /** All failed responses. */
    public List<AgentResponse> failures() {
        return captured.stream().filter(r -> !r.isSuccess() && !r.isSkipped()).collect(Collectors.toList());
    }

    /** True if any response content contains {@code fragment} (case-insensitive). */
    public boolean anyContentContains(String fragment) {
        return captured.stream()
                .filter(AgentResponse::isSuccess)
                .filter(AgentResponse::hasContent)
                .anyMatch(r -> r.content().toLowerCase().contains(fragment.toLowerCase()));
    }

    // ── Assertion helpers ─────────────────────────────────────────────

    /** Fails if any captured response was not successful. */
    public void assertAllSucceeded() {
        var failed = failures();
        if (!failed.isEmpty()) {
            String details = failed.stream()
                    .map(r -> r.agentName() + ": " + r.errorMessage())
                    .collect(Collectors.joining(", "));
            throw new AssertionError("Expected all responses to succeed but got failures: " + details);
        }
    }

    /** Fails if the number of captured responses does not equal {@code expected}. */
    public void assertCount(int expected) {
        if (captured.size() != expected) {
            throw new AssertionError(
                "Expected " + expected + " captured responses but got " + captured.size());
        }
    }

    @Override
    public String toString() {
        return "ResponseCaptor{count=" + captured.size()
               + ", successes=" + successes().size()
               + ", failures=" + failures().size() + "}";
    }
}
