package io.tutoros.mastery;

import io.tutoros.model.AssessmentFeedback;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MasteryService — single entry point for every "the learner answered
 * something" event in the system. Owns the SM-2 spaced-repetition logic
 * and the (score, ease, interval) state-machine on every {@link ConceptMastery}.
 *
 * Inputs converge here from three places:
 *   1. {@code SessionController.submitAnswer} — tutor-turn practice
 *      questions, fed via {@link AssessmentFeedback}.
 *   2. {@code QuizController.submit} — quiz answers (one record per
 *      question, source="quiz").
 *   3. {@code ReviewController.answer} — review-queue cards, source="review".
 *
 * SM-2 implementation notes:
 *   - 4-grade scale ({@link MasteryGrade}) instead of the original 0–5
 *     Anki scale because we already produce that shape from
 *     {@code AssessmentFeedback.masteryDelta}.
 *   - {@code ease} clamped to ≥ 1.3 (the canonical floor; below that the
 *     interval growth degenerates).
 *   - First-pass intervals: 1 day on rep-1, 6 days on rep-2 — matches
 *     Anki / SM-2 published defaults.
 *   - AGAIN resets reps to 0 and pulls ease down by 0.20 (more aggressive
 *     than vanilla SM-2's 0.20 because tutor questions are typically
 *     harder than flashcards).
 *
 * Score (0..1) is tracked SEPARATELY from SM-2 state because the UI
 * heatmaps and the curriculum planner's gap detection both want a
 * smoothed score, not a binary "due/not due". Score moves by 0.10 per
 * GOOD/EASY (with momentum), and -0.10 per AGAIN. HARD nudges +0.03.
 *
 * Threading: {@link MasteryGraphStore#save} is the only mutation point;
 * we read-modify-write per concept. Concurrent same-concept writes
 * (e.g. two tabs, fast double-clicks) may produce a last-writer-wins
 * race; acceptable in v1, fixable later with optimistic locking on the
 * JDBC backend.
 */
public class MasteryService {

    private final MasteryGraphStore store;
    /** Canonical "now" — overridable for deterministic tests. */
    private final java.util.function.Supplier<Instant> clock;

    public MasteryService(MasteryGraphStore store) {
        this(store, Instant::now);
    }

    /** Test ctor — pass a fixed clock to make scheduling deterministic. */
    public MasteryService(MasteryGraphStore store,
                          java.util.function.Supplier<Instant> clock) {
        this.store = store;
        this.clock = clock != null ? clock : Instant::now;
    }

    // ── Read API ─────────────────────────────────────────────────────

    public Optional<ConceptMastery> find(String learnerId, String subject, String concept) {
        return store.find(learnerId, subject, concept);
    }

    public List<ConceptMastery> listForSubject(String learnerId, String subject) {
        return store.listForSubject(learnerId, subject);
    }

    /**
     * Pull up to {@code limit} concepts due for review as of the service's
     * clock. Most overdue first; never-seen concepts surface at the top.
     */
    public List<ConceptMastery> dueForReview(String learnerId, String subject, int limit) {
        return store.dueForReview(learnerId, subject, clock.get(), limit);
    }

    public int countForSubject(String learnerId, String subject) {
        return store.countForSubject(learnerId, subject);
    }

    // ── Write API ────────────────────────────────────────────────────

    /**
     * Convenience that converts an {@link AssessmentFeedback} into a
     * {@link MasteryGrade} and records it. Centralises the "what does a
     * masteryDelta of 0.05 actually mean?" decision so tutor and quiz
     * paths stay consistent.
     */
    public ConceptMastery recordFromFeedback(String learnerId, String subject,
                                             String concept, AssessmentFeedback feedback,
                                             String source) {
        MasteryGrade grade = gradeFromFeedback(feedback);
        return recordOutcome(learnerId, subject, concept, grade, source);
    }

    /**
     * Apply one SM-2 step to a (learner, subject, concept) and persist
     * the new row. Returns the row AFTER the update so callers can show
     * the new score / next due timestamp without a follow-up read.
     */
    public ConceptMastery recordOutcome(String learnerId, String subject,
                                        String concept, MasteryGrade grade,
                                        String source) {
        if (learnerId == null || subject == null || concept == null) {
            throw new IllegalArgumentException("learnerId, subject, concept all required");
        }
        if (grade == null) grade = MasteryGrade.GOOD;

        ConceptMastery row = store.find(learnerId, subject, concept).orElseGet(() -> {
            ConceptMastery fresh = new ConceptMastery();
            fresh.learnerId = learnerId;
            fresh.subject   = subject;
            fresh.concept   = concept;
            return fresh;
        });

        Instant now = clock.get();

        // ── SM-2 update ─────────────────────────────────────────────
        applySm2(row, grade);

        // ── Score update — smoothed, drives UI heatmaps ─────────────
        row.score = clamp01(row.score + scoreDelta(grade));

        // ── Schedule next review ───────────────────────────────────
        row.lastSeenAt   = now;
        row.nextReviewAt = now.plus(Duration.ofDays(Math.max(1, row.intervalDays)));

        // ── History ────────────────────────────────────────────────
        row.appendHistory(new MasteryHistoryEntry(
            now, grade, row.score, source != null ? source : "manual"));

        store.save(row);
        return row;
    }

    // ── SM-2 core ────────────────────────────────────────────────────

    /**
     * Vanilla SM-2 with a 4-grade scale. Mutates the row's reps / ease /
     * intervalDays in place. See class-level comment for rationale on
     * the AGAIN ease drop and the rep-1/rep-2 default intervals.
     */
    static void applySm2(ConceptMastery row, MasteryGrade grade) {
        switch (grade) {
            case AGAIN -> {
                row.reps = 0;
                row.intervalDays = 1;
                row.ease = Math.max(1.3, row.ease - 0.20);
            }
            case HARD -> {
                row.reps += 1;
                row.intervalDays = nextInterval(row, 1.2);
                row.ease = Math.max(1.3, row.ease - 0.15);
            }
            case GOOD -> {
                row.reps += 1;
                row.intervalDays = nextInterval(row, row.ease);
                // ease unchanged
            }
            case EASY -> {
                row.reps += 1;
                row.intervalDays = nextInterval(row, row.ease + 0.15);
                row.ease = row.ease + 0.10;
            }
        }
    }

    /**
     * SM-2 interval growth. First two reps are constants (1 day, 6 days);
     * after that, each interval is the previous one × growth multiplier.
     */
    private static int nextInterval(ConceptMastery row, double growth) {
        if (row.reps <= 1) return 1;
        if (row.reps == 2) return 6;
        int prev = Math.max(1, row.intervalDays);
        return (int) Math.max(1, Math.round(prev * growth));
    }

    private static double scoreDelta(MasteryGrade grade) {
        return switch (grade) {
            case AGAIN -> -0.10;
            case HARD  -> +0.03;
            case GOOD  -> +0.10;
            case EASY  -> +0.15;
        };
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /**
     * Map an {@link AssessmentFeedback} into a {@link MasteryGrade}.
     * The agent's {@code masteryDelta} field already encodes the
     * outcome shape ( +0.10 first-time correct / +0.05 with hints /
     * -0.05 incorrect ); we just bin it into the 4-grade scale.
     */
    static MasteryGrade gradeFromFeedback(AssessmentFeedback f) {
        if (f == null) return MasteryGrade.GOOD;
        double d = f.masteryDelta;
        if (d <= -0.01) return MasteryGrade.AGAIN;
        if (d <   0.07) return MasteryGrade.HARD;
        if (d <   0.12) return MasteryGrade.GOOD;
        return MasteryGrade.EASY;
    }
}
