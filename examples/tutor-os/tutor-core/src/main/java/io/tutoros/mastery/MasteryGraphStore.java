package io.tutoros.mastery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MasteryGraphStore — persistence SPI for the per-learner mastery graph.
 *
 * Same shape as the SquadOS {@code MemoryStore} contract (M1): a small
 * surface so a Postgres / Redis implementation can be dropped in later
 * with no caller changes. The in-process impl that ships with this PR
 * (M3-A) keeps everything in a {@code ConcurrentHashMap}; Postgres is
 * a follow-up PR via the same recipe used for {@code PgVectorEpisodicStore}.
 *
 * The store is the dumb persistence layer — it does NOT compute SM-2,
 * decide what's "due", or apply ranking. That's {@link MasteryService}'s
 * job. The store just round-trips {@link ConceptMastery} rows.
 *
 * Threading: implementations MUST be safe for concurrent reads and
 * single-writer-per-key. The in-process default uses a
 * {@code ConcurrentHashMap}; multi-writer races on a single concept
 * are still possible but acceptable given that the only writer in v1
 * is the answer-submission path (sequential per session).
 */
public interface MasteryGraphStore {

    /** Read one row, if present. */
    Optional<ConceptMastery> find(String learnerId, String subject, String concept);

    /** Upsert. Implementations may copy-on-write or persist in-place. */
    void save(ConceptMastery row);

    /** All rows for a (learner, subject) — used by UI mastery grids. */
    List<ConceptMastery> listForSubject(String learnerId, String subject);

    /**
     * Rows whose {@code nextReviewAt} is on or before {@code asOf}, sorted
     * by overdueness (most overdue first). Limited to {@code limit}.
     *
     * Implementations decide whether "never seen" rows count as due —
     * the in-process default treats them as maximally overdue, so a
     * brand-new concept surfaces as soon as it's been recorded once.
     */
    List<ConceptMastery> dueForReview(String learnerId, String subject,
                                      Instant asOf, int limit);

    /** Total rows for a learner+subject — cheaper than listForSubject().size() on JDBC backends. */
    int countForSubject(String learnerId, String subject);

    /** Wipe everything for a learner+subject. Used by /progress reset endpoints. */
    void clearForSubject(String learnerId, String subject);
}
