package io.squados.tests;

import io.squados.annotation.Retry;
import io.squados.exception.*;
import io.squados.retry.RetryEngine;

import java.lang.annotation.Annotation;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 23 — v3.5 @Retry transient failure retry with exponential backoff
 *
 * R01 — RetryEngine executes callable once when no failure
 * R02 — RetryEngine retries on exception up to maxAttempts
 * R03 — RetryEngine throws RetryExhaustedException after all attempts fail
 * R04 — RetryExhaustedException carries agentName and attempts
 * R05 — RetryExhaustedException carries totalElapsedMs
 * R06 — RetryEngine does NOT retry RateLimitExceededException
 * R07 — RetryEngine does NOT retry GuardrailException
 * R08 — RetryEngine does NOT retry AgentSecurityException
 * R09 — RetryEngine succeeds on 2nd attempt (transient failure)
 * R10 — RetryEngine succeeds on 3rd attempt (two transient failures)
 * R11 — @Retry annotation has correct defaults (maxAttempts=3, backoffMs=1000)
 * R12 — @Retry annotation target is TYPE
 * R13 — RetryEngine with null retry annotation executes once
 * R14 — RetryExhaustedException wraps original cause
 * R15 — RetryEngine with maxAttempts=1 fails immediately (no retry)
 * R16 — RetryEngine short backoff works (0ms for fast tests)
 */
public class SquadOsPhase23Tests {

    static Retry retryWith(int maxAttempts, long backoffMs) {
        return new Retry() {
            public Class<? extends Annotation> annotationType() { return Retry.class; }
            public int   maxAttempts()  { return maxAttempts; }
            public long  backoffMs()    { return backoffMs; }
            public float multiplier()   { return 1.0f; }  // no growth for fast tests
            public long  maxBackoffMs() { return 100L; }
        };
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase23Tests();
        String[] tests = {
            "R01_executesOnceOnSuccess",
            "R02_retriesOnException",
            "R03_throwsRetryExhausted",
            "R04_exhaustedCarriesNameAndAttempts",
            "R05_exhaustedCarriesElapsedMs",
            "R06_doesNotRetryRateLimit",
            "R07_doesNotRetryGuardrail",
            "R08_doesNotRetrySecurityException",
            "R09_succeedsOnSecondAttempt",
            "R10_succeedsOnThirdAttempt",
            "R11_retryAnnotationDefaults",
            "R12_retryAnnotationTargetType",
            "R13_nullRetryExecutesOnce",
            "R14_exhaustedWrapsOriginalCause",
            "R15_maxAttempts1FailsImmediately",
            "R16_shortBackoffWorks",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 23 - v3.5 @Retry             ║");
        System.out.println("╚══════════════════════════════════════════════╝\n");
        for (String name : tests) {
            try {
                t.getClass().getDeclaredMethod(name).invoke(t);
                System.out.printf("  ✓ %s%n", name); passed++;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable c = e.getCause();
                System.out.printf("  ✗ %s%n    -> %s: %s%n",
                    name, c.getClass().getSimpleName(), c.getMessage()); failed++;
            } catch (Exception e) {
                System.out.printf("  ✗ %s%n    -> %s%n", name, e.getMessage()); failed++;
            }
        }
        System.out.printf("%n  Results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) { System.out.println("\n  PHASE 23 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 23 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.5 @Retry exponential backoff operational.\n"); }
    }

    void R01_executesOnceOnSuccess() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryEngine.execute(retryWith(3, 0L), "agent",
            () -> { count.incrementAndGet(); return "ok"; });
        assertEquals("ok", result, "result");
        assertEquals(1, count.get(), "executed once");
    }

    void R02_retriesOnException() {
        AtomicInteger count = new AtomicInteger(0);
        try {
            RetryEngine.execute(retryWith(3, 0L), "agent",
                () -> { count.incrementAndGet(); throw new RuntimeException("transient"); });
        } catch (RetryExhaustedException e) { /* expected */ }
        assertEquals(3, count.get(), "retried 3 times");
    }

    void R03_throwsRetryExhausted() {
        try {
            RetryEngine.execute(retryWith(2, 0L), "myAgent",
                () -> { throw new RuntimeException("boom"); });
            throw new AssertionError("Expected RetryExhaustedException");
        } catch (RetryExhaustedException e) {
            assertNotNull(e, "exception thrown");
        }
    }

    void R04_exhaustedCarriesNameAndAttempts() {
        try {
            RetryEngine.execute(retryWith(2, 0L), "TestAgent",
                () -> { throw new RuntimeException("fail"); });
        } catch (RetryExhaustedException e) {
            assertEquals("TestAgent", e.getAgentName(), "agent name");
            assertEquals(2, e.getAttempts(), "attempts");
        }
    }

    void R05_exhaustedCarriesElapsedMs() {
        try {
            RetryEngine.execute(retryWith(2, 0L), "agent",
                () -> { throw new RuntimeException("fail"); });
        } catch (RetryExhaustedException e) {
            assertTrue(e.getTotalElapsedMs() >= 0, "elapsed ms non-negative");
        }
    }

    void R06_doesNotRetryRateLimit() {
        AtomicInteger count = new AtomicInteger(0);
        try {
            RetryEngine.execute(retryWith(3, 0L), "agent",
                () -> { count.incrementAndGet();
                        throw new RateLimitExceededException("a", "calls", "limit"); });
        } catch (RateLimitExceededException e) { /* expected */ }
        assertEquals(1, count.get(), "RateLimitExceededException not retried");
    }

    void R07_doesNotRetryGuardrail() {
        AtomicInteger count = new AtomicInteger(0);
        try {
            RetryEngine.execute(retryWith(3, 0L), "agent",
                () -> { count.incrementAndGet();
                        throw new GuardrailException("filter", "type", "blocked"); });
        } catch (GuardrailException e) { /* expected */ }
        assertEquals(1, count.get(), "GuardrailException not retried");
    }

    void R08_doesNotRetrySecurityException() {
        AtomicInteger count = new AtomicInteger(0);
        try {
            RetryEngine.execute(retryWith(3, 0L), "agent",
                () -> { count.incrementAndGet();
                        throw new AgentSecurityException("security violation"); });
        } catch (AgentSecurityException e) { /* expected */ }
        assertEquals(1, count.get(), "AgentSecurityException not retried");
    }

    void R09_succeedsOnSecondAttempt() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryEngine.execute(retryWith(3, 0L), "agent",
            () -> {
                if (count.incrementAndGet() == 1) throw new RuntimeException("first fail");
                return "success";
            });
        assertEquals("success", result, "succeeds on 2nd");
        assertEquals(2, count.get(), "called twice");
    }

    void R10_succeedsOnThirdAttempt() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryEngine.execute(retryWith(3, 0L), "agent",
            () -> {
                int c = count.incrementAndGet();
                if (c < 3) throw new RuntimeException("fail " + c);
                return "done";
            });
        assertEquals("done", result, "succeeds on 3rd");
        assertEquals(3, count.get(), "called 3 times");
    }

    void R11_retryAnnotationDefaults() {
        // Check default annotation values via a real @Retry-annotated class
        @Retry
        class Sample {}
        Retry ann = Sample.class.getAnnotation(Retry.class);
        assertEquals(3, ann.maxAttempts(), "default maxAttempts=3");
        assertEquals(1000L, ann.backoffMs(), "default backoffMs=1000");
        assertEquals(2.0f, ann.multiplier(), "default multiplier=2.0");
    }

    void R12_retryAnnotationTargetType() {
        java.lang.annotation.Target target = io.squados.annotation.Retry.class
            .getAnnotation(java.lang.annotation.Target.class);
        assertNotNull(target, "@Target present");
        boolean hasType = false;
        for (java.lang.annotation.ElementType et : target.value()) {
            if (et == java.lang.annotation.ElementType.TYPE) hasType = true;
        }
        assertTrue(hasType, "@Retry targets TYPE");
    }

    void R13_nullRetryExecutesOnce() {
        AtomicInteger count = new AtomicInteger(0);
        String result = RetryEngine.execute(null, "agent",
            () -> { count.incrementAndGet(); return "result"; });
        assertEquals("result", result, "result returned");
        assertEquals(1, count.get(), "executed once");
    }

    void R14_exhaustedWrapsOriginalCause() {
        RuntimeException cause = new RuntimeException("root cause");
        try {
            RetryEngine.execute(retryWith(1, 0L), "agent",
                () -> { throw cause; });
        } catch (RetryExhaustedException e) {
            assertEquals(cause, e.getCause(), "wraps original cause");
        }
    }

    void R15_maxAttempts1FailsImmediately() {
        AtomicInteger count = new AtomicInteger(0);
        try {
            RetryEngine.execute(retryWith(1, 0L), "agent",
                () -> { count.incrementAndGet(); throw new RuntimeException("fail"); });
        } catch (RetryExhaustedException e) {
            assertEquals(1, count.get(), "called exactly once");
        }
    }

    void R16_shortBackoffWorks() {
        AtomicInteger count = new AtomicInteger(0);
        Retry r = retryWith(3, 1L); // 1ms backoff
        try {
            RetryEngine.execute(r, "agent",
                () -> { count.incrementAndGet(); throw new RuntimeException("x"); });
        } catch (RetryExhaustedException e) {
            assertEquals(3, count.get(), "all 3 attempts made");
        }
    }

    static void assertEquals(Object e, Object a, String msg) {
        if (e == null && a == null) return;
        if (e != null && e.equals(a)) return;
        throw new AssertionError(msg + " — expected <" + e + "> but was <" + a + ">");
    }
    static void assertNotNull(Object a, String msg) {
        if (a != null) return; throw new AssertionError(msg + " — null");
    }
    static void assertTrue(boolean c, String msg) {
        if (c) return; throw new AssertionError(msg + " — expected true");
    }
    static void assertFalse(boolean c, String msg) {
        if (!c) return; throw new AssertionError(msg + " — expected false");
    }
}
