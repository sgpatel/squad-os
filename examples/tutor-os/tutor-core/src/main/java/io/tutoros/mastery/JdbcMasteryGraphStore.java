package io.tutoros.mastery;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Persistent {@link MasteryGraphStore} backed by JDBC.
 *
 * Survives JVM restarts. Same recipe as {@code PgVectorEpisodicStore}
 * from M1 — pure JDBC (no Spring JDBC, no JPA), schema bootstrapped on
 * construction, falls back gracefully when a column already exists.
 *
 * <h2>Schema (auto-created)</h2>
 * <pre>
 *   CREATE TABLE IF NOT EXISTS tutor_mastery (
 *     learner_id     VARCHAR(64)  NOT NULL,
 *     subject        VARCHAR(64)  NOT NULL,
 *     concept        VARCHAR(255) NOT NULL,
 *     score          DOUBLE       NOT NULL DEFAULT 0,
 *     ease           DOUBLE       NOT NULL DEFAULT 2.5,
 *     interval_days  INT          NOT NULL DEFAULT 0,
 *     reps           INT          NOT NULL DEFAULT 0,
 *     last_seen_at   TIMESTAMP,
 *     next_review_at TIMESTAMP,
 *     history        TEXT,
 *     updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     PRIMARY KEY (learner_id, subject, concept)
 *   );
 *   CREATE INDEX IF NOT EXISTS tutor_mastery_due_idx
 *     ON tutor_mastery (learner_id, subject, next_review_at);
 * </pre>
 *
 * <h2>Why TEXT for history (not jsonb)</h2>
 * Postgres jsonb is great but H2 doesn't support it, and we want
 * JdbcMasteryGraphStoreTest to run without a Postgres container. The
 * history field is a per-row, append-only log used only for the UI
 * timeline; we serialise it as compact JSON inside Java so the column
 * type doesn't matter to query plans.
 *
 * <h2>Threading</h2>
 * Each call opens a connection from the pool and closes it. Concurrent
 * single-row writes against the same key would last-writer-win without
 * row-level locking; v1 callers (answer endpoint, review answer
 * endpoint) are sequential per learner so this is acceptable. A future
 * follow-up can add SELECT … FOR UPDATE inside an upsert.
 */
public class JdbcMasteryGraphStore implements MasteryGraphStore {

    private final DataSource dataSource;

    public JdbcMasteryGraphStore(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureSchema();
    }

    @Override
    public Optional<ConceptMastery> find(String learnerId, String subject, String concept) {
        String sql = "SELECT * FROM tutor_mastery " +
            "WHERE learner_id = ? AND subject = ? AND concept = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, learnerId);
            ps.setString(2, lower(subject));
            ps.setString(3, lower(concept));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(fromRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] mastery find failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void save(ConceptMastery row) {
        if (row == null) throw new IllegalArgumentException("row");
        String upsert =
            "MERGE INTO tutor_mastery KEY(learner_id, subject, concept) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        // Postgres equivalent (used at runtime — H2 understands MERGE,
        // Postgres needs ON CONFLICT). We pick the right variant based on
        // the active database product.
        String upsertPg =
            "INSERT INTO tutor_mastery (learner_id, subject, concept, score, ease, " +
            "interval_days, reps, last_seen_at, next_review_at, history, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (learner_id, subject, concept) DO UPDATE SET " +
            "score = EXCLUDED.score, ease = EXCLUDED.ease, " +
            "interval_days = EXCLUDED.interval_days, reps = EXCLUDED.reps, " +
            "last_seen_at = EXCLUDED.last_seen_at, " +
            "next_review_at = EXCLUDED.next_review_at, " +
            "history = EXCLUDED.history, updated_at = CURRENT_TIMESTAMP";

        try (Connection c = dataSource.getConnection()) {
            String product = safeProductName(c);
            String sql = product.contains("postgres") ? upsertPg : upsert;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, row.learnerId);
                ps.setString(2, lower(row.subject));
                ps.setString(3, lower(row.concept));
                ps.setDouble(4, row.score);
                ps.setDouble(5, row.ease);
                ps.setInt(6, row.intervalDays);
                ps.setInt(7, row.reps);
                ps.setTimestamp(8, row.lastSeenAt != null ? Timestamp.from(row.lastSeenAt) : null);
                ps.setTimestamp(9, row.nextReviewAt != null ? Timestamp.from(row.nextReviewAt) : null);
                ps.setString(10, encodeHistory(row.history));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] mastery save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConceptMastery> listForSubject(String learnerId, String subject) {
        String sql = "SELECT * FROM tutor_mastery " +
            "WHERE learner_id = ? AND subject = ? ORDER BY concept";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, learnerId);
            ps.setString(2, lower(subject));
            return readAll(ps);
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] mastery list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConceptMastery> dueForReview(String learnerId, String subject,
                                             Instant asOf, int limit) {
        if (asOf == null) asOf = Instant.now();
        if (limit <= 0)   limit = 50;
        // NULL nextReviewAt rows must surface — they're never-seen and
        // most-overdue by convention. Postgres + H2 both honour the
        // explicit NULLS FIRST hint, so it's portable.
        String sql = "SELECT * FROM tutor_mastery " +
            "WHERE learner_id = ? AND subject = ? " +
            "  AND (next_review_at IS NULL OR next_review_at <= ?) " +
            "ORDER BY next_review_at NULLS FIRST, next_review_at ASC " +
            "LIMIT ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, learnerId);
            ps.setString(2, lower(subject));
            ps.setTimestamp(3, Timestamp.from(asOf));
            ps.setInt(4, limit);
            return readAll(ps);
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] mastery due failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int countForSubject(String learnerId, String subject) {
        String sql = "SELECT COUNT(*) FROM tutor_mastery WHERE learner_id = ? AND subject = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, learnerId);
            ps.setString(2, lower(subject));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public void clearForSubject(String learnerId, String subject) {
        String sql = "DELETE FROM tutor_mastery WHERE learner_id = ? AND subject = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, learnerId);
            ps.setString(2, lower(subject));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[TutorOS] mastery clear failed: " + e.getMessage(), e);
        }
    }

    // ── Schema bootstrap ─────────────────────────────────────────────

    private void ensureSchema() {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS tutor_mastery (" +
            "  learner_id     VARCHAR(64)  NOT NULL, " +
            "  subject        VARCHAR(64)  NOT NULL, " +
            "  concept        VARCHAR(255) NOT NULL, " +
            "  score          DOUBLE PRECISION NOT NULL DEFAULT 0, " +
            "  ease           DOUBLE PRECISION NOT NULL DEFAULT 2.5, " +
            "  interval_days  INT          NOT NULL DEFAULT 0, " +
            "  reps           INT          NOT NULL DEFAULT 0, " +
            "  last_seen_at   TIMESTAMP, " +
            "  next_review_at TIMESTAMP, " +
            "  history        TEXT, " +
            "  updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "  PRIMARY KEY (learner_id, subject, concept)" +
            ")",
            "CREATE INDEX IF NOT EXISTS tutor_mastery_due_idx " +
            "ON tutor_mastery (learner_id, subject, next_review_at)"
        };
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            for (String sql : ddl) {
                try { st.execute(sql); }
                catch (SQLException e) { /* table/index may already exist */ }
            }
        } catch (SQLException e) {
            System.err.println("[TutorOS] mastery schema bootstrap failed: " + e.getMessage());
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────

    private static List<ConceptMastery> readAll(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<ConceptMastery> out = new ArrayList<>();
            while (rs.next()) out.add(fromRow(rs));
            return out;
        }
    }

    private static ConceptMastery fromRow(ResultSet rs) throws SQLException {
        ConceptMastery row = new ConceptMastery();
        row.learnerId    = rs.getString("learner_id");
        row.subject      = rs.getString("subject");
        row.concept      = rs.getString("concept");
        row.score        = rs.getDouble("score");
        row.ease         = rs.getDouble("ease");
        row.intervalDays = rs.getInt("interval_days");
        row.reps         = rs.getInt("reps");
        Timestamp ls     = rs.getTimestamp("last_seen_at");
        row.lastSeenAt   = ls != null ? ls.toInstant() : null;
        Timestamp nr     = rs.getTimestamp("next_review_at");
        row.nextReviewAt = nr != null ? nr.toInstant() : null;
        row.history      = decodeHistory(rs.getString("history"));
        return row;
    }

    private static String safeProductName(Connection c) {
        try { return c.getMetaData().getDatabaseProductName().toLowerCase(); }
        catch (SQLException e) { return ""; }
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(); }

    // ── History codec — compact JSON, no Jackson dep ────────────────

    /**
     * Serialise the bounded {@code history} list into a single TEXT
     * column. Hand-rolled JSON to avoid pulling Jackson into tutor-core
     * just for one nested array; the schema is fixed and known.
     */
    static String encodeHistory(List<MasteryHistoryEntry> history) {
        if (history == null || history.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (MasteryHistoryEntry e : history) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"at\":\"").append(e.at != null ? e.at.toString() : "").append("\",");
            sb.append("\"grade\":\"").append(e.grade != null ? e.grade.name() : "GOOD").append("\",");
            sb.append("\"score\":").append(e.score).append(',');
            sb.append("\"source\":\"").append(escape(e.source)).append("\"");
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    /**
     * Parse the JSON written by {@link #encodeHistory}. Lenient — any
     * field that fails to parse is skipped, never thrown, so a partial
     * or older row doesn't poison reads.
     */
    static List<MasteryHistoryEntry> decodeHistory(String json) {
        List<MasteryHistoryEntry> out = new ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) return out;
        // Minimal forgiving parser — relies on the strict shape we wrote.
        // Strips array brackets, splits on `},{`, parses each object.
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return out;
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) return out;
        for (String chunk : inner.split("(?<=\\}),(?=\\{)")) {
            try {
                String s = chunk.trim();
                if (s.startsWith("{")) s = s.substring(1);
                if (s.endsWith("}"))   s = s.substring(0, s.length() - 1);
                MasteryHistoryEntry e = new MasteryHistoryEntry();
                for (String field : splitFields(s)) {
                    int colon = field.indexOf(':');
                    if (colon < 0) continue;
                    String key = field.substring(0, colon).trim().replaceAll("[\"']", "");
                    String val = field.substring(colon + 1).trim();
                    switch (key) {
                        case "at"    -> e.at     = val.isEmpty() ? null
                                                  : Instant.parse(stripQuotes(val));
                        case "grade" -> e.grade  = MasteryGrade.valueOf(stripQuotes(val));
                        case "score" -> e.score  = Double.parseDouble(val);
                        case "source"-> e.source = stripQuotes(val);
                        default -> { /* ignore unknown keys */ }
                    }
                }
                out.add(e);
            } catch (RuntimeException badEntry) {
                // Skip malformed history entries; one bad row shouldn't
                // poison the whole list.
            }
        }
        return out;
    }

    /** Split a JSON object body on top-level commas (strings quoted). */
    private static List<String> splitFields(String body) {
        List<String> out = new ArrayList<>();
        int start = 0;
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) inString = !inString;
            else if (c == ',' && !inString) {
                out.add(body.substring(start, i));
                start = i + 1;
            }
        }
        out.add(body.substring(start));
        return out;
    }

    private static String stripQuotes(String v) {
        String t = v.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            return t.substring(1, t.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return t;
    }

    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
