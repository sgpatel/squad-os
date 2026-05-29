package io.tutoros.book;

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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcBookStoreTest — exercises the JDBC book repo on embedded H2.
 *
 * <p>Random per-test database name keeps tests fully isolated; same
 * harness shape as JdbcMasteryGraphStoreTest. The store's DDL is
 * portable (TEXT for body + concepts; MERGE/ON CONFLICT picked at
 * runtime) so the same code runs against Postgres in production.
 */
class JdbcBookStoreTest {

    private static final String L = "learner-1";

    private DataSource ds;
    private JdbcBookStore store;

    @BeforeEach
    void freshDatabase() {
        String url = "jdbc:h2:mem:books-" + UUID.randomUUID() +
                     ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        ds = new DriverManagerDS(url, "sa", "");
        store = new JdbcBookStore(ds);
    }

    // ── Roundtrip ────────────────────────────────────────────────────

    @Test
    void saveAndFindRoundTripsBookWithChapters() {
        Book book = bookOf("Algebra Made Simple", "Mathematics", "Doe", 88);
        book.id = "book_round1";
        book.chapters.add(chapter(1, "Chapter 1: Numbers",
            "Numbers are the building blocks.", 1, 8,
            List.of("natural numbers", "integers")));
        book.chapters.add(chapter(2, "Chapter 2: Equations",
            "An equation is two expressions joined by =.", 9, 22,
            List.of("linear equation", "quadratic equation")));

        store.save(book);

        Book back = store.findById("book_round1").orElseThrow();
        assertEquals("book_round1", back.id);
        assertEquals(L, back.learnerId);
        assertEquals("mathematics", back.subject);
        assertEquals("Algebra Made Simple", back.title);
        assertEquals("Doe", back.author);
        assertEquals(88, back.totalPages);
        assertEquals(2, back.chapters().size());
        assertEquals("Chapter 1: Numbers", back.chapters().get(0).title);
        assertEquals(List.of("natural numbers", "integers"),
            back.chapters().get(0).concepts());
        assertEquals(1, back.chapters().get(0).pageStart);
        assertEquals(8, back.chapters().get(0).pageEnd);
        assertEquals("An equation is two expressions joined by =.",
            back.chapters().get(1).body);
    }

    @Test
    void saveOnExistingKeyReplacesChapters() {
        Book book = bookOf("Original", "Maths", null, 10);
        book.id = "book_replace";
        book.chapters.add(chapter(1, "First", "old body", 1, 5, List.of("alpha")));
        book.chapters.add(chapter(2, "Second", "old body 2", 6, 10, List.of("beta")));
        store.save(book);

        // Replace with a totally different chapter set.
        book.title = "Revised";
        book.chapters.clear();
        book.chapters.add(chapter(1, "Only", "new body", 1, 10, List.of("gamma")));
        store.save(book);

        Book back = store.findById("book_replace").orElseThrow();
        assertEquals("Revised", back.title);
        assertEquals(1, back.chapters().size(),
            "save() must atomically replace chapters, not accumulate");
        assertEquals("Only", back.chapters().get(0).title);
        assertEquals(List.of("gamma"), back.chapters().get(0).concepts());
    }

    // ── Lists ────────────────────────────────────────────────────────

    @Test
    void listForLearnerReturnsBooksNewestFirst() {
        Book a = bookOf("Older", "Maths", null, 10);
        a.id = "book_older";
        a.uploadedAt = Instant.parse("2026-01-01T00:00:00Z");
        store.save(a);

        Book b = bookOf("Newer", "Maths", null, 10);
        b.id = "book_newer";
        b.uploadedAt = Instant.parse("2026-02-01T00:00:00Z");
        store.save(b);

        List<Book> out = store.listForLearner(L);
        assertEquals(2, out.size());
        assertEquals("book_newer", out.get(0).id,
            "most-recently-uploaded book must come first");
    }

    @Test
    void listForSubjectFiltersCaseInsensitively() {
        Book maths = bookOf("Algebra", "Mathematics", null, 10);
        maths.id = "book_m";
        store.save(maths);

        Book bio = bookOf("Cells", "Biology", null, 10);
        bio.id = "book_b";
        store.save(bio);

        assertEquals(1, store.listForSubject(L, "MATHEMATICS").size(),
            "case-insensitive subject filter");
        assertEquals(1, store.listForSubject(L, "biology").size());
        assertEquals(2, store.countForLearner(L));
    }

    // ── Delete ───────────────────────────────────────────────────────

    @Test
    void deleteWipesBookAndChapters() {
        Book book = bookOf("Disposable", "Maths", null, 5);
        book.id = "book_del";
        book.chapters.add(chapter(1, "One", "body", 1, 5, List.of("x")));
        store.save(book);
        assertEquals(1, store.countForLearner(L));

        store.deleteById("book_del");
        assertEquals(Optional.empty(), store.findById("book_del"));
        assertEquals(0, store.countForLearner(L));
    }

    // ── Concept codec ────────────────────────────────────────────────

    @Test
    void conceptCodecRoundTripsEmbeddedQuotesAndBackslashes() {
        // Stress the JSON codec — strings with " and \ must survive
        // the encode/decode cycle so a textbook with "Newton's Laws"
        // or LaTeX-ish snippets doesn't poison the column.
        List<String> in = List.of(
            "newton's laws",
            "schrödinger's \"cat\"",
            "back\\slash term"
        );
        String encoded = JdbcBookStore.encodeConcepts(in);
        List<String> decoded = JdbcBookStore.decodeConcepts(encoded);
        assertEquals(in, decoded);
    }

    @Test
    void conceptCodecIsLenientOnMalformedInput() {
        assertTrue(JdbcBookStore.decodeConcepts(null).isEmpty());
        assertTrue(JdbcBookStore.decodeConcepts("").isEmpty());
        assertTrue(JdbcBookStore.decodeConcepts("[]").isEmpty());
        assertTrue(JdbcBookStore.decodeConcepts("garbage").isEmpty());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static Book bookOf(String title, String subject, String author, int pages) {
        Book b = new Book();
        b.learnerId  = L;
        b.subject    = subject != null ? subject.toLowerCase() : null;
        b.title      = title;
        b.author     = author;
        b.totalPages = pages;
        b.uploadedAt = Instant.parse("2026-03-01T00:00:00Z");
        return b;
    }

    private static Chapter chapter(int number, String title, String body,
                                   int pageStart, int pageEnd, List<String> concepts) {
        Chapter c = new Chapter();
        c.number    = number;
        c.title     = title;
        c.summary   = Chapter.defaultSummary(body);
        c.pageStart = pageStart;
        c.pageEnd   = pageEnd;
        c.body      = body;
        c.setConcepts(concepts);
        return c;
    }

    /** Minimal DataSource — same shim used by JdbcMasteryGraphStoreTest. */
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
}
