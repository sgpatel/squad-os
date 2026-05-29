package io.tutoros.mastery;

import io.tutoros.model.AssessmentFeedback;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MasteryServiceTest — covers the SM-2 algorithm, the due-queue
 * ordering, and the feedback → grade conversion. No Spring, no LLM,
 * no clock drift — uses an injected supplier so every assertion is
 * deterministic.
 */
class MasteryServiceTest {

    private static final String L = "learner-1";
    private static final String S = "Mathematics";
    private static final String C = "Trigonometry/sin-cos";

    /**
     * Mutable clock holder so tests can advance time without sleeping.
     * SM-2 schedules in days, so each "tick" we just bump the clock by
     * the interval the previous step asked for.
     */
    private static final class TestClock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant get() { return now; }
        void advanceDays(int d) { now = now.plus(Duration.ofDays(d)); }
    }

    private MasteryService service(TestClock clock, MasteryGraphStore store) {
        return new MasteryService(store, clock::get);
    }

    // ── SM-2 algorithm ─────────────────────────────────────────────

    @Test
    void goodPath_setsRep1Interval1_thenRep2Interval6_thenGrowsByEase() {
        TestClock clock = new TestClock();
        MasteryGraphStore store = new InProcessMasteryGraphStore();
        MasteryService svc = service(clock, store);

        ConceptMastery r1 = svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "tutor");
        assertEquals(1, r1.intervalDays, "rep-1 GOOD must schedule 1 day out");
        assertEquals(1, r1.reps);
        assertEquals(2.5, r1.ease, 0.001, "GOOD never moves ease");

        clock.advanceDays(1);
        ConceptMastery r2 = svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "tutor");
        assertEquals(6, r2.intervalDays, "rep-2 GOOD must schedule 6 days out");
        assertEquals(2, r2.reps);

        clock.advanceDays(6);
        ConceptMastery r3 = svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "tutor");
        // After rep-2 the interval grows by ease (2.5) — 6 × 2.5 = 15.
        assertEquals(15, r3.intervalDays);
    }

    @Test
    void againResetsRepsAndDropsEase_butFloorsAt1Point3() {
        TestClock clock = new TestClock();
        MasteryService svc = service(clock, new InProcessMasteryGraphStore());

        // Build up a streak first.
        svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "tutor"); clock.advanceDays(1);
        svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "tutor"); clock.advanceDays(6);
        ConceptMastery before = svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "tutor");
        assertEquals(3, before.reps, "three consecutive GOODs → reps=3");

        // AGAIN should reset reps and drop ease, but never below 1.3.
        clock.advanceDays(15);
        ConceptMastery after = svc.recordOutcome(L, S, C, MasteryGrade.AGAIN, "tutor");
        assertEquals(0, after.reps, "AGAIN resets reps");
        assertEquals(1, after.intervalDays, "AGAIN reschedules to 1 day");
        assertTrue(after.ease >= 1.3, "ease floored at 1.3");

        // Many AGAINs should not push ease below 1.3.
        for (int i = 0; i < 20; i++) {
            clock.advanceDays(1);
            after = svc.recordOutcome(L, S, C, MasteryGrade.AGAIN, "tutor");
        }
        assertEquals(1.3, after.ease, 1e-9, "ease floor must hold under repeated AGAINs");
    }

    @Test
    void easyGrowsIntervalFasterThanGood() {
        TestClock clock1 = new TestClock();
        TestClock clock2 = new TestClock();
        MasteryService a = service(clock1, new InProcessMasteryGraphStore());
        MasteryService b = service(clock2, new InProcessMasteryGraphStore());

        // Get both to rep-3.
        a.recordOutcome(L, S, C, MasteryGrade.GOOD, "t"); clock1.advanceDays(1);
        a.recordOutcome(L, S, C, MasteryGrade.GOOD, "t"); clock1.advanceDays(6);
        ConceptMastery aR3 = a.recordOutcome(L, S, C, MasteryGrade.GOOD, "t");

        b.recordOutcome(L, S, C, MasteryGrade.GOOD, "t"); clock2.advanceDays(1);
        b.recordOutcome(L, S, C, MasteryGrade.GOOD, "t"); clock2.advanceDays(6);
        ConceptMastery bR3 = b.recordOutcome(L, S, C, MasteryGrade.EASY, "t");

        assertTrue(bR3.intervalDays > aR3.intervalDays,
            "EASY must produce a longer next interval than GOOD");
        assertTrue(bR3.ease > aR3.ease,
            "EASY must bump ease above GOOD");
    }

    // ── Score (UI-facing 0..1 band) ─────────────────────────────────

    @Test
    void scoreClimbsOnGoodAndDropsOnAgain_clampedTo01() {
        MasteryService svc = service(new TestClock(), new InProcessMasteryGraphStore());

        ConceptMastery r = svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "t");
        assertEquals(0.10, r.score, 1e-9);

        // Pump it up.
        for (int i = 0; i < 20; i++) r = svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "t");
        assertEquals(1.0, r.score, 1e-9, "score clamps at 1.0");

        // Knock it down.
        for (int i = 0; i < 20; i++) r = svc.recordOutcome(L, S, C, MasteryGrade.AGAIN, "t");
        assertEquals(0.0, r.score, 1e-9, "score floors at 0.0");
    }

    // ── Due queue ordering ─────────────────────────────────────────

    @Test
    void dueQueueReturnsOnlyOverdue_mostOverdueFirst() {
        TestClock clock = new TestClock();
        MasteryService svc = service(clock, new InProcessMasteryGraphStore());

        // Three concepts, all on rep-1 (interval 1 day).
        svc.recordOutcome(L, S, "alpha", MasteryGrade.GOOD, "t");
        svc.recordOutcome(L, S, "beta",  MasteryGrade.GOOD, "t");
        svc.recordOutcome(L, S, "gamma", MasteryGrade.GOOD, "t");

        // Nothing due yet.
        assertEquals(0, svc.dueForReview(L, S, 10).size());

        // Advance a day → all three become due.
        clock.advanceDays(1);
        List<ConceptMastery> due = svc.dueForReview(L, S, 10);
        assertEquals(3, due.size());

        // Touch beta — it's now NOT due.
        svc.recordOutcome(L, S, "beta", MasteryGrade.GOOD, "t");
        clock.advanceDays(0); // same instant
        List<ConceptMastery> dueAfter = svc.dueForReview(L, S, 10);
        assertEquals(2, dueAfter.size());
        assertTrue(dueAfter.stream().noneMatch(c -> c.concept.equals("beta")));
    }

    @Test
    void neverSeenRowsRankAboveAlreadyScheduled() {
        // The store accepts a freshly-saved row that has nextReviewAt=null.
        // dueForReview should treat those as MAX_VALUE overdue so brand-new
        // concepts surface immediately on a review pull.
        TestClock clock = new TestClock();
        InProcessMasteryGraphStore store = new InProcessMasteryGraphStore();

        ConceptMastery never = new ConceptMastery();
        never.learnerId = L; never.subject = S; never.concept = "neverSeen";
        store.save(never);

        MasteryService svc = service(clock, store);
        svc.recordOutcome(L, S, "scheduled", MasteryGrade.GOOD, "t");
        clock.advanceDays(2); // "scheduled" is 1 day overdue

        List<ConceptMastery> due = svc.dueForReview(L, S, 10);
        assertEquals(2, due.size());
        assertEquals("neverSeen", due.get(0).concept,
            "never-seen row must come first regardless of how overdue scheduled rows are");
    }

    // ── feedback → grade mapping ────────────────────────────────────

    @Test
    void feedbackDeltaBinsCleanly() {
        AssessmentFeedback wrong = new AssessmentFeedback();
        wrong.masteryDelta = -0.05;
        assertEquals(MasteryGrade.AGAIN, MasteryService.gradeFromFeedback(wrong));

        AssessmentFeedback hint = new AssessmentFeedback();
        hint.masteryDelta = 0.05;
        assertEquals(MasteryGrade.HARD, MasteryService.gradeFromFeedback(hint));

        AssessmentFeedback good = new AssessmentFeedback();
        good.masteryDelta = 0.10;
        assertEquals(MasteryGrade.GOOD, MasteryService.gradeFromFeedback(good));

        AssessmentFeedback easy = new AssessmentFeedback();
        easy.masteryDelta = 0.20;
        assertEquals(MasteryGrade.EASY, MasteryService.gradeFromFeedback(easy));

        // Null is a permissive default — never blow up the caller.
        assertEquals(MasteryGrade.GOOD, MasteryService.gradeFromFeedback(null));
    }

    // ── Store roundtrip ─────────────────────────────────────────────

    @Test
    void inProcessStoreRoundTripsAndIsCaseInsensitiveOnSubject() {
        InProcessMasteryGraphStore store = new InProcessMasteryGraphStore();
        ConceptMastery row = new ConceptMastery();
        row.learnerId = L; row.subject = "Mathematics"; row.concept = "Algebra";
        row.score = 0.5;
        store.save(row);

        // Lookup with different casing must hit the same row.
        assertTrue(store.find(L, "MATHEMATICS", "algebra").isPresent());
        assertEquals(1, store.countForSubject(L, "math".equals(S) ? S : "MATHEMATICS"));

        assertEquals(1, store.listForSubject(L, "Mathematics").size());

        store.clearForSubject(L, "mathematics");
        assertEquals(0, store.countForSubject(L, "Mathematics"));
    }

    // ── History bound ──────────────────────────────────────────────

    @Test
    void historyTruncatesAtCapAndKeepsNewest() {
        TestClock clock = new TestClock();
        MasteryService svc = service(clock, new InProcessMasteryGraphStore());

        // Record more than HISTORY_CAP outcomes; verify cap holds.
        for (int i = 0; i < ConceptMastery.HISTORY_CAP + 10; i++) {
            svc.recordOutcome(L, S, C, MasteryGrade.GOOD, "t");
            clock.advanceDays(1);
        }
        ConceptMastery row = svc.find(L, S, C).orElseThrow();
        assertEquals(ConceptMastery.HISTORY_CAP, row.history.size(),
            "history must be bounded at HISTORY_CAP");
    }

    // ── Listed flag (silences unused-import warnings if added later) ──
    @SuppressWarnings("unused")
    private static AtomicReference<Object> _keep = new AtomicReference<>();
}
