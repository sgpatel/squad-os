package io.squados.tests;

import io.squados.annotation.*;
import io.squados.durable.*;
import io.squados.exception.DurableWorkflowException;
import io.squados.llm.*;

import java.util.Optional;

/**
 * Phase 21 — v3.5 @DurableAgent workflow lifecycle
 *
 * W01 — InProcessDurableStore save/load round-trip
 * W02 — InProcessDurableStore load returns empty for unknown id
 * W03 — InProcessDurableStore delete removes workflow
 * W04 — InProcessDurableStore exists returns true/false correctly
 * W05 — WorkflowState starts in PENDING status
 * W06 — WorkflowState.start() transitions to RUNNING
 * W07 — WorkflowState.pause() transitions to PAUSED
 * W08 — WorkflowState.resume() transitions back to RUNNING
 * W09 — WorkflowState.complete() stores finalOutput and status COMPLETED
 * W10 — WorkflowState.fail() stores errorMessage and status FAILED
 * W11 — WorkflowState.addStep() increments getNextStepIndex
 * W12 — WorkflowStep.success() stores success=true and output
 * W13 — WorkflowStep.failure() stores success=false and error
 * W14 — DurableEngine returns cached result for COMPLETED workflow (idempotent)
 * W15 — DurableEngine.pause() transitions workflow to PAUSED
 * W16 — DurableEngine.resume() transitions PAUSED workflow to RUNNING
 * W17 — DurableEngine throws DurableWorkflowException for PAUSED workflow on submit
 * W18 — WorkflowStatus enum covers PENDING, RUNNING, PAUSED, COMPLETED, FAILED
 */
public class SquadOsPhase21Tests {

    static class EchoLlm implements LlmPort {
        @Override public LlmResponse chat(String s, String u, LlmOptions o) {
            return new LlmResponse("DURABLE:" + u, 5, 10, "mock");
        }
        @Override public <T> T chatStructured(String s, String u, Class<T> t, LlmOptions o) { return null; }
    }

    public static void main(String[] args) {
        int passed = 0, failed = 0;
        var t = new SquadOsPhase21Tests();
        String[] tests = {
            "W01_storeRoundTrip",
            "W02_storeLoadUnknown",
            "W03_storeDelete",
            "W04_storeExists",
            "W05_stateStartsPending",
            "W06_stateStartTransition",
            "W07_statePauseTransition",
            "W08_stateResumeTransition",
            "W09_stateComplete",
            "W10_stateFail",
            "W11_stateAddStep",
            "W12_stepSuccess",
            "W13_stepFailure",
            "W14_durableEngineIdempotent",
            "W15_durableEnginePause",
            "W16_durableEngineResume",
            "W17_durableEngineRejectsPaused",
            "W18_workflowStatusEnum",
        };
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   SquadOS Phase 21 - v3.5 @DurableAgent      ║");
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
        if (failed > 0) { System.out.println("\n  PHASE 21 GATE: FAILED\n"); System.exit(1); }
        else { System.out.println("\n  PHASE 21 GATE: ALL TESTS PASSED ✓");
               System.out.println("  v3.5 @DurableAgent workflows operational.\n"); }
    }

    void W01_storeRoundTrip() {
        InProcessDurableStore store = new InProcessDurableStore();
        WorkflowState state = new WorkflowState("wf-001", AgentRole.ANALYST, "test input");
        store.save(state);
        Optional<WorkflowState> loaded = store.load("wf-001");
        assertTrue(loaded.isPresent(), "loaded present");
        assertEquals("wf-001", loaded.get().getWorkflowId(), "workflow id");
        assertEquals("test input", loaded.get().getInitialInput(), "initial input");
    }

    void W02_storeLoadUnknown() {
        InProcessDurableStore store = new InProcessDurableStore();
        Optional<WorkflowState> loaded = store.load("nonexistent");
        assertFalse(loaded.isPresent(), "empty for unknown id");
    }

    void W03_storeDelete() {
        InProcessDurableStore store = new InProcessDurableStore();
        WorkflowState state = new WorkflowState("wf-del", AgentRole.ANALYST, "input");
        store.save(state);
        assertTrue(store.exists("wf-del"), "exists before delete");
        store.delete("wf-del");
        assertFalse(store.exists("wf-del"), "gone after delete");
    }

    void W04_storeExists() {
        InProcessDurableStore store = new InProcessDurableStore();
        assertFalse(store.exists("nope"), "false for unknown");
        store.save(new WorkflowState("yes", AgentRole.ANALYST, "x"));
        assertTrue(store.exists("yes"), "true after save");
    }

    void W05_stateStartsPending() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        assertEquals(WorkflowStatus.PENDING, s.getStatus(), "starts PENDING");
    }

    void W06_stateStartTransition() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        s.start();
        assertEquals(WorkflowStatus.RUNNING, s.getStatus(), "RUNNING after start");
        assertTrue(s.isRunning(), "isRunning true");
    }

    void W07_statePauseTransition() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        s.start();
        s.pause();
        assertEquals(WorkflowStatus.PAUSED, s.getStatus(), "PAUSED status");
        assertTrue(s.isPaused(), "isPaused true");
    }

    void W08_stateResumeTransition() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        s.start(); s.pause(); s.resume();
        assertEquals(WorkflowStatus.RUNNING, s.getStatus(), "RUNNING after resume");
    }

    void W09_stateComplete() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        s.start();
        s.complete("final answer");
        assertEquals(WorkflowStatus.COMPLETED, s.getStatus(), "COMPLETED");
        assertEquals("final answer", s.getFinalOutput(), "final output");
        assertTrue(s.isCompleted(), "isCompleted");
    }

    void W10_stateFail() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        s.start();
        s.fail("step blew up");
        assertEquals(WorkflowStatus.FAILED, s.getStatus(), "FAILED");
        assertEquals("step blew up", s.getErrorMessage(), "error message");
        assertTrue(s.isFailed(), "isFailed");
    }

    void W11_stateAddStep() {
        WorkflowState s = new WorkflowState("id", AgentRole.ANALYST, "in");
        assertEquals(0, s.getNextStepIndex(), "starts at 0");
        s.addStep(WorkflowStep.success(0, "step1", "output1"));
        assertEquals(1, s.getNextStepIndex(), "1 after first step");
        s.addStep(WorkflowStep.success(1, "step2", "output2"));
        assertEquals(2, s.getNextStepIndex(), "2 after second step");
    }

    void W12_stepSuccess() {
        WorkflowStep step = WorkflowStep.success(0, "validate", "VALID");
        assertEquals(0, step.stepIndex(), "step index");
        assertEquals("validate", step.stepName(), "step name");
        assertEquals("VALID", step.output(), "output");
        assertTrue(step.success(), "success true");
        assertNotNull(step.completedAt(), "completedAt set");
    }

    void W13_stepFailure() {
        WorkflowStep step = WorkflowStep.failure(1, "enrich", "timeout");
        assertFalse(step.success(), "success false");
        assertEquals("timeout", step.output(), "error in output");
    }

    void W14_durableEngineIdempotent() {
        InProcessDurableStore store = new InProcessDurableStore();
        WorkflowState completed = new WorkflowState("wf-c", AgentRole.ANALYST, "input");
        completed.start();
        completed.complete("cached result");
        store.save(completed);

        // Mock registry with no agents — should still return cached result
        io.squados.context.AgentRegistry registry = new io.squados.context.AgentRegistry();
        DurableEngine engine = new DurableEngine(store, registry);

        io.squados.agent.AgentResponse resp = engine.submit(AgentRole.ANALYST, "wf-c", "input");
        assertTrue(resp.isSuccess(), "returns cached success");
        assertEquals("cached result", resp.content(), "cached content");
    }

    void W15_durableEnginePause() {
        InProcessDurableStore store = new InProcessDurableStore();
        WorkflowState state = new WorkflowState("wf-p", AgentRole.ANALYST, "x");
        state.start();
        store.save(state);

        io.squados.context.AgentRegistry registry = new io.squados.context.AgentRegistry();
        DurableEngine engine = new DurableEngine(store, registry);
        engine.pause("wf-p");

        Optional<WorkflowState> after = store.load("wf-p");
        assertTrue(after.isPresent(), "state still present");
        assertTrue(after.get().isPaused(), "state is paused");
    }

    void W16_durableEngineResume() {
        InProcessDurableStore store = new InProcessDurableStore();
        WorkflowState state = new WorkflowState("wf-r", AgentRole.ANALYST, "x");
        state.start();
        state.pause();
        store.save(state);

        io.squados.context.AgentRegistry registry = new io.squados.context.AgentRegistry();
        DurableEngine engine = new DurableEngine(store, registry);
        engine.resume("wf-r");

        Optional<WorkflowState> after = store.load("wf-r");
        assertTrue(after.get().isRunning(), "state is running after resume");
    }

    void W17_durableEngineRejectsPaused() {
        InProcessDurableStore store = new InProcessDurableStore();
        WorkflowState state = new WorkflowState("wf-x", AgentRole.ANALYST, "input");
        state.start();
        state.pause();
        store.save(state);

        io.squados.context.AgentRegistry registry = new io.squados.context.AgentRegistry();
        DurableEngine engine = new DurableEngine(store, registry);
        try {
            engine.submit(AgentRole.ANALYST, "wf-x", "input");
            throw new AssertionError("Expected DurableWorkflowException");
        } catch (DurableWorkflowException e) {
            assertTrue(e.getMessage().contains("PAUSED"), "error mentions PAUSED");
        }
    }

    void W18_workflowStatusEnum() {
        assertEquals(5, WorkflowStatus.values().length, "5 status values");
        assertNotNull(WorkflowStatus.valueOf("PENDING"), "PENDING");
        assertNotNull(WorkflowStatus.valueOf("RUNNING"), "RUNNING");
        assertNotNull(WorkflowStatus.valueOf("PAUSED"), "PAUSED");
        assertNotNull(WorkflowStatus.valueOf("COMPLETED"), "COMPLETED");
        assertNotNull(WorkflowStatus.valueOf("FAILED"), "FAILED");
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
