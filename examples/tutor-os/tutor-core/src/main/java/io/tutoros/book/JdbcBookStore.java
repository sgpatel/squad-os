package io.tutoros.book;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Persistent {@link BookRepository} backed by JDBC.
 *
 * <p>Survives JVM restarts. Same recipe as
 * {@code JdbcMasteryGraphStore} from M3-JDBC: pure JDBC (no Spring
 * JDBC, no JPA), schema bootstrapped on construction, MERGE-or-ON
 * CONFLICT chosen at runtime so H2 + Postgres both work.
 *
 * <h2>Schema</h2>
 * <pre>
 *   tutor_books      — one row per uploaded book
 *   tutor_chapters   — N rows per book (composite PK book_id + number)
 * </pre>
 * Both tables persist text only; binary PDFs are NOT retained — the
 * extraction service has already chunked the relevant content into
 * chapter bodies. This keeps the database small even after dozens of
 * book uploads and means there's no GDPR concern about retaining the
 * raw upload bytes.
 *
 * <h2>Concepts storage</h2>
 * Per-chapter concept list serialised as a JSON-ish CSV-of-quoted-
 * strings; we avoid a third table (one row per concept) because the
 * concepts only matter <i>as a unit</i> when materialising a chapter,
 * and a string column round-trips cleanly across H2 + Postgres without
 * touching jsonb. {@link #encodeConcepts}/{@link #decodeConcepts}
 * handle the codec.
 */
public class JdbcBookStore implements BookRepository {

    private final DataSource ds;

    public JdbcBookStore(DataSource dataSource) {
        this.ds = dataSource;
        ensureSchema();
    }

    // ── Read API ─────────────────────────────────────────────────────

    @Override
    public Optional<Book> findById(String bookId) {
        if (bookId == null) return Optional.empty();
        try (Connection c = ds.getConnection()) {
            Book b = loadBook(c, bookId);
            return Optional.ofNullable(b);
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] book findById failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Book> listForLearner(String learnerId) {
        if (learnerId == null) return Collections.emptyList();
        return listWhere("learner_id = ?", new Object[]{learnerId});
    }

    @Override
    public List<Book> listForSubject(String learnerId, String subject) {
        if (learnerId == null || subject == null) return Collections.emptyList();
        return listWhere("learner_id = ? AND subject = ?",
            new Object[]{learnerId, subject.trim().toLowerCase()});
    }

    @Override
    public int countForLearner(String learnerId) {
        if (learnerId == null) return 0;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM tutor_books WHERE learner_id = ?")) {
            ps.setString(1, learnerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // ── Write API ────────────────────────────────────────────────────

    @Override
    public void save(Book book) {
        if (book == null || book.id == null || book.id.isBlank()) {
            throw new IllegalArgumentException("Book.id is required");
        }
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                String product = safeProductName(c);
                upsertBook(c, product, book);
                // Replace all chapters atomically — simpler than diffing,
                // and uploads are append-only in PR-1 (no incremental
                // chapter edits yet).
                deleteChapters(c, book.id);
                for (Chapter ch : book.chapters()) insertChapter(c, book.id, ch);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] book save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(String bookId) {
        if (bookId == null) return;
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                deleteChapters(c, bookId);
                try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM tutor_books WHERE id = ?")) {
                    ps.setString(1, bookId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] book delete failed: " + e.getMessage(), e);
        }
    }

    // ── Schema ───────────────────────────────────────────────────────

    private void ensureSchema() {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS tutor_books (" +
            "  id          VARCHAR(64)  PRIMARY KEY, " +
            "  learner_id  VARCHAR(64)  NOT NULL, " +
            "  subject     VARCHAR(64), " +
            "  title       VARCHAR(512) NOT NULL, " +
            "  author      VARCHAR(255), " +
            "  total_pages INT          NOT NULL DEFAULT 0, " +
            "  uploaded_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")",
            "CREATE INDEX IF NOT EXISTS tutor_books_learner_idx " +
            "ON tutor_books (learner_id, uploaded_at DESC)",
            "CREATE TABLE IF NOT EXISTS tutor_chapters (" +
            "  book_id    VARCHAR(64) NOT NULL REFERENCES tutor_books(id), " +
            "  number     INT          NOT NULL, " +
            "  title      VARCHAR(512) NOT NULL, " +
            "  summary    TEXT, " +
            "  page_start INT          NOT NULL DEFAULT 0, " +
            "  page_end   INT          NOT NULL DEFAULT 0, " +
            "  body       TEXT, " +
            "  concepts   TEXT, " +
            "  PRIMARY KEY (book_id, number)" +
            ")"
        };
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String sql : ddl) {
                try { st.execute(sql); }
                catch (SQLException ignored) { /* may already exist */ }
            }
        } catch (SQLException e) {
            System.err.println("[TutorOS] book schema bootstrap failed: " + e.getMessage());
        }
    }

    // ── Internals ────────────────────────────────────────────────────

    /**
     * Single-pass load: book row + all chapter rows via a single LEFT
     * JOIN ordered by chapter number, then folded into a Book + List
     * in Java. One round-trip is plenty for v1 — chapters are bounded
     * (<100 per book) so memory pressure is negligible.
     */
    private Book loadBook(Connection c, String bookId) throws SQLException {
        String sql =
            "SELECT b.id, b.learner_id, b.subject, b.title, b.author, " +
            "       b.total_pages, b.uploaded_at, " +
            "       ch.number, ch.title AS ch_title, ch.summary, " +
            "       ch.page_start, ch.page_end, ch.body, ch.concepts " +
            "FROM tutor_books b LEFT JOIN tutor_chapters ch ON ch.book_id = b.id " +
            "WHERE b.id = ? " +
            "ORDER BY ch.number";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                Book book = null;
                while (rs.next()) {
                    if (book == null) book = readBookHeader(rs);
                    int chNum = rs.getInt("number");
                    if (!rs.wasNull()) book.chapters.add(readChapter(rs));
                }
                return book;
            }
        }
    }

    /**
     * Two-query list: ids first (cheap, single column), then load
     * each book in turn. Avoids fan-out joins on a list endpoint
     * that may return dozens of books.
     */
    private List<Book> listWhere(String where, Object[] args) {
        String sql = "SELECT id FROM tutor_books WHERE " + where +
                     " ORDER BY uploaded_at DESC";
        List<Book> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            List<String> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
            for (String id : ids) {
                Book b = loadBook(c, id);
                if (b != null) out.add(b);
            }
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] book list failed: " + e.getMessage(), e);
        }
        return out;
    }

    private void upsertBook(Connection c, String product, Book b) throws SQLException {
        String sql = product.contains("postgres")
            ? "INSERT INTO tutor_books (id, learner_id, subject, title, author, " +
              "  total_pages, uploaded_at) VALUES (?,?,?,?,?,?,?) " +
              "ON CONFLICT (id) DO UPDATE SET " +
              "  learner_id=EXCLUDED.learner_id, subject=EXCLUDED.subject, " +
              "  title=EXCLUDED.title, author=EXCLUDED.author, " +
              "  total_pages=EXCLUDED.total_pages, uploaded_at=EXCLUDED.uploaded_at"
            : "MERGE INTO tutor_books KEY(id) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.id);
            ps.setString(2, b.learnerId);
            ps.setString(3, b.subject);
            ps.setString(4, b.title);
            ps.setString(5, b.author);
            ps.setInt(6, b.totalPages);
            ps.setTimestamp(7, Timestamp.from(b.uploadedAt != null ? b.uploadedAt : Instant.now()));
            ps.executeUpdate();
        }
    }

    private void deleteChapters(Connection c, String bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "DELETE FROM tutor_chapters WHERE book_id = ?")) {
            ps.setString(1, bookId);
            ps.executeUpdate();
        }
    }

    private void insertChapter(Connection c, String bookId, Chapter ch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO tutor_chapters (book_id, number, title, summary, " +
            "page_start, page_end, body, concepts) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, bookId);
            ps.setInt(2, ch.number);
            ps.setString(3, ch.title);
            ps.setString(4, ch.summary);
            ps.setInt(5, ch.pageStart);
            ps.setInt(6, ch.pageEnd);
            ps.setString(7, ch.body);
            ps.setString(8, encodeConcepts(ch.concepts()));
            ps.executeUpdate();
        }
    }

    private static Book readBookHeader(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.id         = rs.getString("id");
        b.learnerId  = rs.getString("learner_id");
        b.subject    = rs.getString("subject");
        b.title      = rs.getString("title");
        b.author     = rs.getString("author");
        b.totalPages = rs.getInt("total_pages");
        Timestamp t  = rs.getTimestamp("uploaded_at");
        b.uploadedAt = t != null ? t.toInstant() : null;
        return b;
    }

    private static Chapter readChapter(ResultSet rs) throws SQLException {
        Chapter c = new Chapter();
        c.number    = rs.getInt("number");
        c.title     = rs.getString("ch_title");
        c.summary   = rs.getString("summary");
        c.pageStart = rs.getInt("page_start");
        c.pageEnd   = rs.getInt("page_end");
        c.body      = rs.getString("body");
        c.setConcepts(decodeConcepts(rs.getString("concepts")));
        return c;
    }

    // ── Concept codec ────────────────────────────────────────────────

    /**
     * Encode a concept list as JSON-style array of quoted strings.
     * Hand-rolled (no Jackson dep on tutor-core) — round-trips through
     * {@link #decodeConcepts}. Empty list → "[]".
     */
    static String encodeConcepts(List<String> in) {
        if (in == null || in.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String c : in) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(c)).append('"');
        }
        return sb.append(']').toString();
    }

    /** Lenient decode — garbage in → empty list, never thrown. */
    static List<String> decodeConcepts(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank() || raw.equals("[]")) return out;
        String s = raw.trim();
        if (!s.startsWith("[") || !s.endsWith("]")) return out;
        s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty()) return out;
        // Split on top-level commas (we only emit strings).
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            else if (ch == ',' && !inString) {
                addConcept(out, s.substring(start, i));
                start = i + 1;
            }
        }
        addConcept(out, s.substring(start));
        return out;
    }

    private static void addConcept(List<String> out, String chunk) {
        String t = chunk.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            out.add(t.substring(1, t.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
    }

    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeProductName(Connection c) {
        try { return c.getMetaData().getDatabaseProductName().toLowerCase(); }
        catch (SQLException e) { return ""; }
    }
}
