package io.tutoros.mastery;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * InProcessMasteryGraphStore — default {@link MasteryGraphStore} for dev /
 * single-instance deployments. Backed by a {@code ConcurrentHashMap} keyed
 * by {@code (learnerId, subject, concept)} so reads are O(1) per key.
 *
 * Loses everything on JVM restart. Same trade-off as
 * {@code InProcessMemoryStore} from M1; a follow-up PR adds a JDBC
 * implementation that satisfies the same SPI.
 *
 * Sort behaviour for {@link #dueForReview} keeps "never seen" rows
 * (i.e. {@code nextReviewAt == null}) at the top — they should surface
 * immediately on first review pull rather than wait for a synthetic
 * timestamp. After that, rows ordered by overdue magnitude descending.
 */
public class InProcessMasteryGraphStore implements MasteryGraphStore {

    /** Composite-key separator. Plain ASCII so the source stays portable. */
    private static final String SEP = "::";

    private static String key(String learnerId, String subject, String concept) {
        return learnerId + SEP + lower(subject) + SEP + lower(concept);
    }

    /** Prefix matcher for listForSubject / dueForReview / countForSubject. */
    private static String prefix(String learnerId, String subject) {
        return learnerId + SEP + lower(subject) + SEP;
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(); }

    private final Map<String, ConceptMastery> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<ConceptMastery> find(String learnerId, String subject, String concept) {
        return Optional.ofNullable(rows.get(key(learnerId, subject, concept)));
    }

    @Override
    public void save(ConceptMastery row) {
        if (row == null) throw new IllegalArgumentException("row");
        // Mixed-case subjects ("Mathematics" / "mathematics") converge on the
        // same row via the lower-cased key.
        rows.put(key(row.learnerId, row.subject, row.concept), row);
    }

    @Override
    public List<ConceptMastery> listForSubject(String learnerId, String subject) {
        String p = prefix(learnerId, subject);
        return rows.entrySet().stream()
            .filter(e -> e.getKey().startsWith(p))
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparing(r -> lower(r.concept)))
            .collect(Collectors.toList());
    }

    @Override
    public List<ConceptMastery> dueForReview(String learnerId, String subject,
                                             Instant asOf, int limit) {
        if (asOf == null) asOf = Instant.now();
        if (limit <= 0)   limit = 50;
        String p = prefix(learnerId, subject);
        Instant nowFinal = asOf;
        return rows.entrySet().stream()
            .filter(e -> e.getKey().startsWith(p))
            .map(Map.Entry::getValue)
            .filter(r -> r.nextReviewAt == null || !r.nextReviewAt.isAfter(nowFinal))
            // Most overdue first — never-seen rows are MAX_VALUE overdue.
            .sorted(Comparator.<ConceptMastery>comparingLong(r -> r.overdueMillis(nowFinal)).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public int countForSubject(String learnerId, String subject) {
        String p = prefix(learnerId, subject);
        return (int) rows.keySet().stream().filter(k -> k.startsWith(p)).count();
    }

    @Override
    public void clearForSubject(String learnerId, String subject) {
        String p = prefix(learnerId, subject);
        rows.keySet().removeIf(k -> k.startsWith(p));
    }
}
