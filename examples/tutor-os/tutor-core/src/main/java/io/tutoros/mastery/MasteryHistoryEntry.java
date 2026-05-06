package io.tutoros.mastery;

import java.time.Instant;

/**
 * One outcome event for a (learner, subject, concept) triple. Appended to
 * {@link ConceptMastery#history} every time {@link MasteryService#recordOutcome}
 * runs, regardless of source (tutor turn, quiz submission, review queue).
 *
 * Carries {@code source} so downstream consumers (UI charts, parent reports)
 * can split between e.g. tutor-driven turns and review-queue cards.
 */
public class MasteryHistoryEntry {
    public Instant      at;
    public MasteryGrade grade;
    /** Score AFTER this outcome was applied. */
    public double       score;
    /** "tutor" | "quiz" | "review" | "manual" — see callers in MasteryService. */
    public String       source;

    public MasteryHistoryEntry() {}

    public MasteryHistoryEntry(Instant at, MasteryGrade grade, double score, String source) {
        this.at     = at;
        this.grade  = grade;
        this.score  = score;
        this.source = source;
    }
}
