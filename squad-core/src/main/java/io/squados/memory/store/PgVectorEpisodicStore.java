package io.squados.memory.store;

import io.squados.memory.annotation.Importance;
import io.squados.memory.annotation.MemoryType;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production episodic memory store backed by PostgreSQL + pgvector.
 *
 * Replaces InProcessMemoryStore for EPISODIC tier.
 * Data survives JVM restarts — agents remember across sessions.
 *
 * Prerequisites:
 *   1. PostgreSQL with pgvector extension
 *   2. Run the schema migration (see resources/db/migration/V1__squados_memory.sql)
 *
 * Schema (auto-created on first use if CREATE TABLE is allowed):
 * <pre>
 *   CREATE EXTENSION IF NOT EXISTS vector;
 *   CREATE TABLE squad_memories (
 *     id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *     squad_id      VARCHAR(64)  NOT NULL,
 *     agent_id      VARCHAR(64),
 *     session_id    VARCHAR(64)  NOT NULL,
 *     memory_type   VARCHAR(20)  NOT NULL,
 *     content       TEXT         NOT NULL,
 *     embedding     vector(64),
 *     importance    SMALLINT     DEFAULT 2,
 *     decay_score   FLOAT        DEFAULT 1.0,
 *     access_count  INT          DEFAULT 0,
 *     last_accessed TIMESTAMPTZ,
 *     tags          TEXT[],
 *     created_at    TIMESTAMPTZ  DEFAULT NOW()
 *   );
 *   CREATE INDEX ON squad_memories USING hnsw (embedding vector_cosine_ops);
 * </pre>
 *
 * Phase 3 upgrade: increase vector dimensions to 1536 for OpenAI embeddings.
 */
public class PgVectorEpisodicStore implements MemoryStore {

    private final DataSource dataSource;
    private final int        dimensions;

    public PgVectorEpisodicStore(DataSource dataSource, int dimensions) {
        this.dataSource = dataSource;
        this.dimensions = dimensions;
        ensureSchema();
    }

    @Override
    public MemoryType type() { return MemoryType.EPISODIC; }

    @Override
    public void save(MemoryRecord record) {
        String sql = "INSERT INTO squad_memories " +
            "(id, squad_id, agent_id, session_id, memory_type, " +
            "content, embedding, importance, decay_score, tags, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "decay_score = EXCLUDED.decay_score, " +
            "access_count = squad_memories.access_count + 1, " +
            "last_accessed = NOW()";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, java.util.UUID.fromString(record.getId()));
            ps.setString(2, record.getSquadId());
            ps.setString(3, record.getAgentId());
            ps.setString(4, record.getSessionId());
            ps.setString(5, record.getType().name());
            ps.setString(6, record.getContent());
            ps.setString(7, toVectorLiteral(record.getEmbedding()));
            ps.setInt   (8, importanceOrdinal(record.getImportance()));
            ps.setFloat (9, record.getDecayScore());
            ps.setArray (10, conn.createArrayOf("TEXT", record.getTags()));
            ps.setTimestamp(11, Timestamp.from(record.getCreatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[SquadOS] PgVector save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MemoryRecord> retrieve(float[] queryEmbedding, String squadId,
                                        String agentId, int topK, float minScore) {
        if (topK <= 0) return Collections.emptyList();

        // Cosine similarity search via pgvector <=> operator
        // Re-rank by decay_score × importance weight × similarity
        String sqlWithVec = "SELECT *, 1 - (embedding <=> ?::vector) AS similarity " +
            "FROM squad_memories " +
            "WHERE squad_id = ? " +
            "AND (? IS NULL OR agent_id = ? OR agent_id IS NULL) " +
            "AND memory_type = 'EPISODIC' " +
            "AND decay_score >= 0.1 " +
            "ORDER BY embedding <=> ?::vector LIMIT ?";
        String sqlNoVec = "SELECT *, 1.0 AS similarity " +
            "FROM squad_memories " +
            "WHERE squad_id = ? " +
            "AND memory_type = 'EPISODIC' " +
            "AND decay_score >= 0.1 " +
            "ORDER BY decay_score DESC, created_at DESC LIMIT ?";
        String sql = queryEmbedding != null ? sqlWithVec : sqlNoVec;

        List<MemoryRecord> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (queryEmbedding != null) {
                String vecLit = toVectorLiteral(queryEmbedding);
                ps.setString(1, vecLit);
                ps.setString(2, squadId);
                ps.setString(3, agentId);
                ps.setString(4, agentId);
                ps.setString(5, vecLit);
                ps.setInt   (6, topK * 3); // over-fetch for re-ranking
            } else {
                ps.setString(1, squadId);
                ps.setInt   (2, topK);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MemoryRecord r = fromResultSet(rs);
                    float sim     = rs.getFloat("similarity");
                    if (r.rankingScore(sim) >= minScore) {
                        r.recordAccess();
                        results.add(r);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[SquadOS] PgVector retrieve failed: " + e.getMessage(), e);
        }

        // Re-rank by combined score, take topK
        return results.stream()
            .sorted(Comparator.comparingDouble(r ->
                -r.rankingScore(0.8f))) // approximate re-rank without sim
            .limit(topK)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteBySession(String sessionId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM squad_memories WHERE session_id = ? AND memory_type = 'EPISODIC'")) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[SquadOS] PgVector delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int count() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM squad_memories WHERE memory_type = 'EPISODIC' AND decay_score >= 0.1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * Apply nightly decay to all episodic records.
     * Call this from a @Scheduled job at 2am.
     * Returns count of records soft-evicted (decay_score dropped below 0.1).
     */
    public int applyDecay() {
        String sql = "UPDATE squad_memories SET " +
            "decay_score = decay_score * CASE importance " +
            "WHEN 3 THEN 0.995 WHEN 2 THEN 0.985 ELSE 0.970 END " +
            "WHERE memory_type = 'EPISODIC' " +
            "RETURNING decay_score";
        int evicted = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (rs.getFloat(1) < 0.1f) evicted++;
            }
        } catch (SQLException e) {
            System.err.println("[SquadOS] Decay job error: " + e.getMessage());
        }
        return evicted;
    }

    // ── Schema ────────────────────────────────────────────────────

    private void ensureSchema() {
        String[] ddl = {
            "CREATE EXTENSION IF NOT EXISTS vector",
            "CREATE TABLE IF NOT EXISTS squad_memories (" +
            "  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid()," +
            "  squad_id      VARCHAR(64)  NOT NULL," +
            "  agent_id      VARCHAR(64)," +
            "  session_id    VARCHAR(64)  NOT NULL," +
            "  memory_type   VARCHAR(20)  NOT NULL," +
            "  content       TEXT         NOT NULL," +
            "  embedding     vector(" + dimensions + ")," +
            "  importance    SMALLINT     DEFAULT 2," +
            "  decay_score   FLOAT        DEFAULT 1.0," +
            "  access_count  INT          DEFAULT 0," +
            "  last_accessed TIMESTAMPTZ," +
            "  tags          TEXT[]       DEFAULT '{}'," +
            "  created_at    TIMESTAMPTZ  DEFAULT NOW()" +
            ")",
            "CREATE INDEX IF NOT EXISTS squad_memories_embedding_idx " +
            "ON squad_memories USING hnsw (embedding vector_cosine_ops)",
            "CREATE INDEX IF NOT EXISTS squad_memories_squad_idx " +
            "ON squad_memories (squad_id, memory_type, decay_score)"
        };
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            for (String sql : ddl) {
                try { st.execute(sql); }
                catch (SQLException e) { /* extension/table may already exist */ }
            }
        } catch (SQLException e) {
            System.err.printf("[SquadOS] Schema check failed: %s%n", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static String toVectorLiteral(float[] v) {
        if (v == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static int importanceOrdinal(Importance imp) {
        return switch (imp) { case LOW -> 1; case MEDIUM -> 2; case HIGH -> 3; };
    }

    private static MemoryRecord fromResultSet(ResultSet rs) throws SQLException {
        Importance imp = switch (rs.getInt("importance")) {
            case 3  -> Importance.HIGH;
            case 1  -> Importance.LOW;
            default -> Importance.MEDIUM;
        };
        Array tagsArray = rs.getArray("tags");
        String[] tags = tagsArray != null ? (String[]) tagsArray.getArray() : new String[0];
        MemoryRecord r = new MemoryRecord(
            rs.getString("squad_id"),
            rs.getString("agent_id"),
            rs.getString("session_id"),
            MemoryType.EPISODIC,
            rs.getString("content"),
            imp, tags
        );
        r.setDecayScore(rs.getFloat("decay_score"));
        return r;
    }
}
