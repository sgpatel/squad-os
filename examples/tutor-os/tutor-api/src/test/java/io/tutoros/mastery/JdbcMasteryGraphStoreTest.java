package io.tutoros.mastery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcMasteryGraphStoreTest — exercises the JDBC backend on an embedded
 * H2 database.
 *
 * The store's DDL is intentionally portable (TEXT instead of jsonb,
 * MERGE-or-ON-CONFLICT chosen by detected product) so the same code path
 * the test runs is what production runs against Postgres. Each test
 * spins up an isolated in-memory H2 instance via a per-test
 * randomly-named database name to avoid cross-test contamination.
 */
class JdbcMasteryGraphStoreTest {

    private static final String L = "learner-1";
    private static final String S = "Mathematics";

    private DataSource ds;
    private JdbcMasteryGraphStore store;

    @BeforeEach
    void freshDatabase() {
        // Random DB name per test → fully isolated, no cleanup needed.
        // MODE=PostgreSQL keeps NULLS FIRST + DOUBLE PRECISION valid.
        String url = "jdbc:h2:mem:mastery-" + UUID.randomUUID() +
                     ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        ds = new DriverManagerDS(url, "sa", "");
        store = new JdbcMasteryGraphStore(ds);
    }

    /** Minimal DataSource — avoids dragging spring-jdbc just for one ctor. */
    private static final class DriverManagerDS implements DataSource {
        private final String url, user, pw;
        DriverManagerDS(String url, String user, String pw) {
            this.url = url; this.user = user; this.pw = pw;
        }
        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, pw);
        }
        @Override public Connection getConnection(String u, String p) throws SQLException {
            return DriverManager.getConnection(url, u, p);
        }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    // ── Roundtrip ────────────────────────────────────────────────────

    @Test
    void saveAndFindRoundTripsAllFields() {
        ConceptMastery row = new ConceptMastery();
        row.learnerId    = L;
        row.subject      = S;
        row.concept      = "Algebra/quadratics";
        row.score        = 0.42;
        row.ease         = 2.6;
        row.intervalDays = 7;
        row.reps         = 3;
        row.lastSeenAt   = Instant.parse("2026-04-01T10:00:00Z");
        row.nextReviewAt = Instant.parse("2026-04-08T10:00:00Z");
        row.appendHistory(new MasteryHistoryEntry(
            Instant.parse("2026-04-01T10:00:00Z"),
            MasteryGrade.GOOD, 0.42, "tutor"));

        store.save(row);

        ConceptMastery back = store.find(L, S, "Algebra/quadratics").orElseThrow();
        assertEquals(L, back.learnerId);
        assertEquals("mathematics", back.subject, "subject persisted lowercase");
        assertEquals("algebra/quadratics", back.concept, "concept persisted lowercase");
        assertEquals(0.42, back.score, 1e-9);
        assertEquals(2.6, back.ease, 1e-9);
        assertEquals(7, back.intervalDays);
        assertEquals(3, back.reps);
        assertEquals(Instant.parse("2026-04-01T10:00:00Z"), back.lastSeenAt);
        assertEquals(Instant.parse("2026-04-08T10:00:00Z"), back.nextReviewAt);
        assertEquals(1, back.history.size());
        assertEquals(MasteryGrade.GOOD, back.history.get(0).grade);
        assertEquals(0.42, back.history.get(0).score, 1e-9);
        assertEquals("tutor", back.history.get(0).source);
    }

    // ── Upsert ───────────────────────────────────────────────────────

    @Test
    void saveOnExistingKeyUpserts() {
        ConceptMastery row = new ConceptMastery();
        row.learnerId = L; row.subject = S; row.concept = "limits";
        row.score = 0.20;
        store.save(row);

        // Mutate and save again — must overwrite, not duplicate.
        row.score = 0.55;
        row.intervalDays = 6;
        store.save(row);

        ConceptMastery back = store.find(L, S, "limits").orElseThrow();
        assertEquals(0.55, back.score, 1e-9);
        assertEquals(6, back.intervalDays);
        assertEquals(1, store.countForSubject(L, S),
            "upsert must not produce a second row");
    }

    // ── List + due ordering ──────────────────────────────────────────

    @Test
    void dueQueueReturnsNullsFirst_thenMostOverdue() {
        // Three rows: one never-seen (NULL nextReviewAt), one due 2 days
        // ago, one due 1 hour ago. Expected order: never-seen → 2d → 1h.
        ConceptMastery never = bareRow("never");
        store.save(never);

        ConceptMastery twoDays = bareRow("twoDays");
        twoDays.nextReviewAt = Instant.now().minus(java.time.Duration.ofDays(2));
        store.save(twoDays);

        ConceptMastery oneHour = bareRow("oneHour");
        oneHour.nextReviewAt = Instant.now().minus(java.time.Duration.ofHours(1));
        store.save(oneHour);

        List<ConceptMastery> due = store.dueForReview(L, S, Instant.now(), 10);
        assertEquals(3, due.size());
        assertEquals("never",   due.get(0).concept, "NULLS FIRST surfaces never-seen at top");
        assertEquals("twodays", due.get(1).concept, "more-overdue row before less-overdue");
        assertEquals("onehour", due.get(2).concept);
    }

    @Test
    void dueQueueExcludesNotYetDueRows() {
        ConceptMastery future = bareRow("future");
        future.nextReviewAt = Instant.now().plus(java.time.Duration.ofDays(5));
        store.save(future);

        assertEquals(0, store.dueForReview(L, S, Instant.now(), 10).size(),
            "future-due rows must not appear in the queue");
    }

    // ── Subject scope ────────────────────────────────────────────────

    @Test
    void listForSubjectIsCaseInsensitiveAndFiltered() {
        store.save(bareRow("a"));
        store.save(bareRow("b"));

        // Different subject — must NOT show up in Mathematics list.
        ConceptMastery other = bareRow("c");
        other.subject = "Biology";
        store.save(other);

        assertEquals(2, store.listForSubject(L, "MATHEMATICS").size(),
            "case-insensitive subject lookup");
        assertEquals(2, store.countForSubject(L, "Mathematics"));
        assertEquals(1, store.countForSubject(L, "Biology"));
    }

    @Test
    void clearForSubjectWipesScope() {
        store.save(bareRow("a"));
        store.save(bareRow("b"));
        ConceptMastery other = bareRow("c");
        other.subject = "Biology";
        store.save(other);

        store.clearForSubject(L, "Mathematics");
        assertEquals(0, store.countForSubject(L, "Mathematics"));
        assertEquals(1, store.countForSubject(L, "Biology"),
            "clear must be scoped to the named subject");
    }

    // ── History codec ────────────────────────────────────────────────

    @Test
    void historyCodecRoundTripsMixedSources() {
        // Three entries, one with a quote in the source string to exercise
        // the escape path in encodeHistory / decodeHistory.
        List<MasteryHistoryEntry> seed = List.of(
            new MasteryHistoryEntry(Instant.parse("2026-01-01T00:00:00Z"),
                MasteryGrade.GOOD,  0.10, "tutor"),
            new MasteryHistoryEntry(Instant.parse("2026-01-02T00:00:00Z"),
                MasteryGrade.AGAIN, 0.05, "review"),
            new MasteryHistoryEntry(Instant.parse("2026-01-03T00:00:00Z"),
                MasteryGrade.EASY,  0.20, "quiz \"oral\"")
        );
        String encoded = JdbcMasteryGraphStore.encodeHistory(seed);
        List<MasteryHistoryEntry> decoded = JdbcMasteryGraphStore.decodeHistory(encoded);

        assertEquals(3, decoded.size());
        assertEquals(MasteryGrade.GOOD,  decoded.get(0).grade);
        assertEquals(MasteryGrade.AGAIN, decoded.get(1).grade);
        assertEquals(MasteryGrade.EASY,  decoded.get(2).grade);
        assertEquals("review",           decoded.get(1).source);
        assertEquals("quiz \"oral\"",    decoded.get(2).source,
            "embedded quotes survive the codec");
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), decoded.get(1).at);
    }

    @Test
    void decodeIsLenientOnMalformedJson() {
        // Garbage in → empty list out, no exception. The store keeps
        // serving even if a row's history column was hand-edited.
        assertTrue(JdbcMasteryGraphStore.decodeHistory(null).isEmpty());
        assertTrue(JdbcMasteryGraphStore.decodeHistory("").isEmpty());
        assertTrue(JdbcMasteryGraphStore.decodeHistory("[]").isEmpty());
        assertTrue(JdbcMasteryGraphStore.decodeHistory("not json").isEmpty());
    }

    // ── End-to-end with MasteryService ───────────────────────────────

    @Test
    void masteryServiceWithJdbcBackendBehavesLikeInProcess() {
        // Same SM-2 contract — the JDBC store must be a drop-in for the
        // in-process one. Re-run the smoke that proves three correct
        // answers leave the row at rep=3 / score=0.30.
        MasteryService svc = new MasteryService(store);
        for (int i = 0; i < 3; i++) {
            svc.recordOutcome(L, S, "limits", MasteryGrade.GOOD, "tutor");
        }
        ConceptMastery row = svc.find(L, S, "limits").orElseThrow();
        assertEquals(3, row.reps);
        assertEquals(0.30, row.score, 1e-9);
        assertEquals(3, row.history.size(),
            "history persists across multiple JDBC roundtrips");
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static ConceptMastery bareRow(String concept) {
        ConceptMastery r = new ConceptMastery();
        r.learnerId = L; r.subject = S; r.concept = concept;
        return r;
    }
}
