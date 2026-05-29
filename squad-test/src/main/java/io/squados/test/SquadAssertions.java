package io.squados.test;

import io.squados.agent.AgentResponse;
import io.squados.annotation.AgentRole;
import io.squados.router.SemanticRouteResult;
import io.squados.topology.TopologyResult;

/**
 * Typed assertion helpers for SquadOS test outcomes.
 *
 * All methods throw {@link AssertionError} with a descriptive message on
 * failure — compatible with any test framework (JUnit 4/5, TestNG, plain
 * {@code main()}-style exec-maven-plugin tests).
 *
 * <pre>
 *   SquadAssertions.assertSuccess(response);
 *   SquadAssertions.assertContentContains(response, "expected fragment");
 *   SquadAssertions.assertRole(response, AgentRole.RESEARCHER);
 *
 *   SquadAssertions.assertTopologySuccess(result);
 *   SquadAssertions.assertTopologySteps(result, 3);
 *   SquadAssertions.assertTopologyOutputContains(result, "PublishAgent", "PUBLISHED");
 *
 *   SquadAssertions.assertRouted(routeResult, "ResearchAgent");
 *   SquadAssertions.assertFallback(routeResult);
 *   SquadAssertions.assertConfidenceAbove(routeResult, 0.5f);
 * </pre>
 */
public final class SquadAssertions {

    private SquadAssertions() {}

    // ══════════════════════════════════════════════════════════════════
    // AgentResponse assertions
    // ══════════════════════════════════════════════════════════════════

    /** Fails if the response is not successful. */
    public static void assertSuccess(AgentResponse r) {
        if (r == null)       fail("AgentResponse is null");
        if (!r.isSuccess())  fail("Expected success but got failure: " + r.errorMessage());
    }

    /** Fails if the response is not a failure. */
    public static void assertFailure(AgentResponse r) {
        if (r == null)      fail("AgentResponse is null");
        if (r.isSuccess())  fail("Expected failure but got success: " + r.content());
    }

    /** Fails if the response is not skipped. */
    public static void assertSkipped(AgentResponse r) {
        if (r == null)       fail("AgentResponse is null");
        if (!r.isSkipped())  fail("Expected skipped response but got: " + r);
    }

    /** Fails if the content does not contain {@code fragment} (case-insensitive). */
    public static void assertContentContains(AgentResponse r, String fragment) {
        assertSuccess(r);
        if (!r.content().toLowerCase().contains(fragment.toLowerCase())) {
            fail("Expected content to contain '" + fragment + "' but got: " + r.content());
        }
    }

    /** Fails if the content does not start with {@code prefix} (case-insensitive). */
    public static void assertContentStartsWith(AgentResponse r, String prefix) {
        assertSuccess(r);
        if (!r.content().toLowerCase().startsWith(prefix.toLowerCase())) {
            fail("Expected content to start with '" + prefix + "' but got: " + r.content());
        }
    }

    /** Fails if the agent role does not match. */
    public static void assertRole(AgentResponse r, AgentRole expected) {
        if (r == null) fail("AgentResponse is null");
        if (r.role() != expected) {
            fail("Expected role " + expected + " but got " + r.role());
        }
    }

    /** Fails if the agent name does not match. */
    public static void assertAgentName(AgentResponse r, String expectedName) {
        if (r == null) fail("AgentResponse is null");
        if (!expectedName.equals(r.agentName())) {
            fail("Expected agent '" + expectedName + "' but got '" + r.agentName() + "'");
        }
    }

    /** Fails if total token count is not positive (LLM was actually called). */
    public static void assertTokensRecorded(AgentResponse r) {
        assertSuccess(r);
        if (r.totalTokens() <= 0) {
            fail("Expected tokens > 0 but got " + r.totalTokens());
        }
    }

    /** Fails if the latency exceeds {@code maxMs} milliseconds. */
    public static void assertLatencyBelow(AgentResponse r, long maxMs) {
        if (r == null) fail("AgentResponse is null");
        long ms = r.latency().toMillis();
        if (ms > maxMs) {
            fail("Expected latency < " + maxMs + "ms but got " + ms + "ms");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // TopologyResult assertions
    // ══════════════════════════════════════════════════════════════════

    /** Fails if the topology execution did not succeed. */
    public static void assertTopologySuccess(TopologyResult r) {
        if (r == null)          fail("TopologyResult is null");
        if (!r.isSuccess())     fail("Expected topology success but got: " + r.errorMessage());
    }

    /** Fails if the topology execution did not fail. */
    public static void assertTopologyFailure(TopologyResult r) {
        if (r == null)          fail("TopologyResult is null");
        if (r.isSuccess())      fail("Expected topology failure but result succeeded");
    }

    /** Fails if the number of executed steps does not match. */
    public static void assertTopologySteps(TopologyResult r, int expectedSteps) {
        assertTopologySuccess(r);
        int actual = r.executionOrder().size();
        if (actual != expectedSteps) {
            fail("Expected " + expectedSteps + " topology steps but got " + actual
                 + ": " + r.executionOrder());
        }
    }

    /** Fails if the named agent's output does not contain {@code fragment} (case-insensitive). */
    public static void assertTopologyOutputContains(TopologyResult r,
                                                    String agentName, String fragment) {
        assertTopologySuccess(r);
        var step = r.stepOutputs().get(agentName);
        if (step == null) {
            fail("No output recorded for agent '" + agentName
                 + "'. Steps executed: " + r.executionOrder());
        }
        if (step.content() == null || !step.content().toLowerCase().contains(fragment.toLowerCase())) {
            fail("Expected output for '" + agentName + "' to contain '"
                 + fragment + "' but got: " + step.content());
        }
    }

    /** Fails if the final (last-step) topology output does not contain {@code fragment}. */
    public static void assertTopologyFinalOutputContains(TopologyResult r, String fragment) {
        assertTopologySuccess(r);
        var final_ = r.finalOutput();
        if (final_ == null || final_.content() == null
                || !final_.content().toLowerCase().contains(fragment.toLowerCase())) {
            String content = (final_ == null) ? "null" : final_.content();
            fail("Expected topology final output to contain '" + fragment + "' but got: " + content);
        }
    }

    /** Fails if the execution order list does not contain the agent at the given position. */
    public static void assertTopologyStep(TopologyResult r, int position, String agentName) {
        assertTopologySuccess(r);
        var order = r.executionOrder();
        if (position >= order.size()) {
            fail("Position " + position + " out of bounds — only " + order.size() + " steps");
        }
        String actual = order.get(position);
        if (!agentName.equals(actual)) {
            fail("Expected step[" + position + "] to be '" + agentName + "' but got '" + actual + "'");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // SemanticRouteResult assertions
    // ══════════════════════════════════════════════════════════════════

    /** Fails if routing did not choose the expected agent. */
    public static void assertRouted(SemanticRouteResult r, String expectedAgent) {
        if (r == null) fail("SemanticRouteResult is null");
        if (!expectedAgent.equals(r.agentName())) {
            fail("Expected routing to '" + expectedAgent + "' but got '"
                 + r.agentName() + "' (confidence=" + r.confidence() + ")");
        }
    }

    /** Fails if the fallback was NOT used (i.e., a real agent matched). */
    public static void assertFallback(SemanticRouteResult r) {
        if (r == null)          fail("SemanticRouteResult is null");
        if (!r.usedFallback())  fail("Expected fallback routing but a real agent matched: " + r.agentName());
    }

    /** Fails if the fallback WAS used (i.e., no real agent matched). */
    public static void assertNotFallback(SemanticRouteResult r) {
        if (r == null)         fail("SemanticRouteResult is null");
        if (r.usedFallback())  fail("Expected a real agent match but fallback was used");
    }

    /** Fails if confidence is not above the threshold. */
    public static void assertConfidenceAbove(SemanticRouteResult r, float threshold) {
        if (r == null) fail("SemanticRouteResult is null");
        if (r.confidence() <= threshold) {
            fail("Expected confidence > " + threshold + " but got " + r.confidence());
        }
    }

    /** Fails if confidence is not below the threshold. */
    public static void assertConfidenceBelow(SemanticRouteResult r, float threshold) {
        if (r == null) fail("SemanticRouteResult is null");
        if (r.confidence() >= threshold) {
            fail("Expected confidence < " + threshold + " but got " + r.confidence());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // General helpers
    // ══════════════════════════════════════════════════════════════════

    /** Fails immediately with the given message. */
    public static void fail(String message) {
        throw new AssertionError(message);
    }

    /** Fails if {@code condition} is false. */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message);
    }

    /** Fails if {@code condition} is true. */
    public static void assertFalse(boolean condition, String message) {
        if (condition) fail(message);
    }

    /** Fails if {@code actual} does not equal {@code expected}. */
    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            fail(message + " — expected: <" + expected + "> but was: <" + actual + ">");
        }
    }

    /** Fails if {@code value} is null. */
    public static void assertNotNull(Object value, String message) {
        if (value == null) fail(message);
    }
}
