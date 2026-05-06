package io.tutoros.mastery;

/**
 * Anki-style 4-grade scale fed into the SM-2 scheduler.
 *
 *   AGAIN — wrong, or right with significant effort/hints. Reset
 *           the interval to 1 day and pull `ease` down hard.
 *   HARD  — right but the learner struggled. Slight ease drop, smaller
 *           interval growth.
 *   GOOD  — right with normal effort. Default growth path; ease unchanged.
 *   EASY  — right effortlessly. Ease bumps up, interval grows extra.
 *
 * The mapping from {@link io.tutoros.model.AssessmentFeedback#masteryDelta}
 * lives in {@link MasteryService} so the algorithm has one entry point
 * regardless of whether the answer came from a tutor turn, a quiz, or
 * the review queue.
 */
public enum MasteryGrade {
    AGAIN, HARD, GOOD, EASY;

    /** Convenience for callers that already have an "is the answer correct?" boolean. */
    public static MasteryGrade fromCorrectness(boolean correct, boolean withHints) {
        if (!correct) return AGAIN;
        return withHints ? HARD : GOOD;
    }
}
