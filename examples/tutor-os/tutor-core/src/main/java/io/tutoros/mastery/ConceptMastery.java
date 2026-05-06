package io.tutoros.mastery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row in the per-learner mastery graph.
 *
 * Three pieces of state, kept together because they always update as a unit:
 *
 *   - <b>Mastery score</b>: 0.0..1.0, the learner's current grasp of this
 *     concept. Drives UI heatmaps and the curriculum planner's gap detection.
 *   - <b>SM-2 spacing</b>: {@code ease} (multiplier ≥ 1.3), {@code intervalDays}
 *     (next gap), {@code reps} (consecutive correct streak). Drives the
 *     review queue and the {@code nextReviewAt} timestamp.
 *   - <b>History</b>: bounded list of {@link MasteryHistoryEntry} so the UI
 *     timelines + plateau detection have ground truth without re-querying.
 *
 * Serialisable shape (no Spring / Jackson annotations) so the same class can
 * back the in-process store, an upcoming JDBC store, or a JSON wire payload
 * for {@code GET /api/review/queue/...}. Public mutable fields match the
 * style of {@link io.tutoros.model.LearnerProfile} — kept consistent with
 * the rest of {@code io.tutoros.model}.
 *
 * History is capped at {@link #HISTORY_CAP} entries; older entries are
 * dropped. Plateau detection only needs the last 3–5 outcomes, and an
 * unbounded tail would balloon over a long session.
 */
public class ConceptMastery {

    /** Newest-N policy for history truncation. */
    public static final int HISTORY_CAP = 32;

    public String learnerId;
    public String subject;
    public String concept;

    /** 0.0..1.0 — UI bands: <0.40 weak, 0.40–0.80 learning, ≥0.80 mastered. */
    public double score;

    /**
     * SM-2 ease factor. Anki default is 2.5; the algorithm clamps to ≥1.3
     * because below that the schedule degenerates (intervals stay tiny).
     */
    public double ease = 2.5;

    /** Next-review gap in days. 0 means "due now" (a fresh concept). */
    public int    intervalDays = 0;

    /** Consecutive AGAIN-free reviews — drives interval growth in SM-2. */
    public int    reps = 0;

    public Instant lastSeenAt;
    public Instant nextReviewAt;

    /** Bounded outcome log (oldest first). Capped at {@link #HISTORY_CAP}. */
    public List<MasteryHistoryEntry> history = new ArrayList<>();

    /** Append an entry, truncating to {@link #HISTORY_CAP} from the head. */
    public void appendHistory(MasteryHistoryEntry entry) {
        if (entry == null) return;
        history.add(entry);
        while (history.size() > HISTORY_CAP) history.remove(0);
    }

    /**
     * View of the most recent N entries (newest first). Returned as an
     * unmodifiable list so callers can't push back into the bounded log
     * without going through {@link #appendHistory}.
     */
    public List<MasteryHistoryEntry> recentHistory(int n) {
        if (history == null || history.isEmpty()) return Collections.emptyList();
        int from = Math.max(0, history.size() - n);
        List<MasteryHistoryEntry> tail = new ArrayList<>(history.subList(from, history.size()));
        Collections.reverse(tail);
        return Collections.unmodifiableList(tail);
    }

    /** Convenience: how many milliseconds past {@code nextReviewAt} is "now"? */
    public long overdueMillis(Instant now) {
        if (nextReviewAt == null) return Long.MAX_VALUE;  // never seen → most overdue
        return Math.max(0L, now.toEpochMilli() - nextReviewAt.toEpochMilli());
    }
}
