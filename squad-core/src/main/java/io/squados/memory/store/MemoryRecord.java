package io.squados.memory.store;

import io.squados.memory.annotation.Importance;
import io.squados.memory.annotation.MemoryType;

import java.time.Instant;
import java.util.UUID;

/**
 * A single memory record stored across all four backends.
 */
public class MemoryRecord {

    private final String     id;
    private final String     squadId;
    private final String     agentId;
    private final String     sessionId;
    private final MemoryType type;
    private final String     content;
    private       float[]    embedding;
    private final Importance importance;
    private       float      decayScore;
    private       int        accessCount;
    private       Instant    lastAccessed;
    private final String[]   tags;
    private final Instant    createdAt;

    public MemoryRecord(String squadId, String agentId, String sessionId,
                        MemoryType type, String content,
                        Importance importance, String[] tags) {
        this.id          = UUID.randomUUID().toString();
        this.squadId     = squadId;
        this.agentId     = agentId;
        this.sessionId   = sessionId;
        this.type        = type;
        this.content     = content;
        this.importance  = importance;
        this.decayScore  = 1.0f;
        this.accessCount = 0;
        this.tags        = tags != null ? tags : new String[0];
        this.createdAt   = Instant.now();
    }

    public void recordAccess() {
        this.accessCount++;
        this.lastAccessed = Instant.now();
    }

    /** Combined retrieval score: similarity x decay x importanceWeight */
    public float rankingScore(float similarity) {
        float w = switch (importance) {
            case HIGH   -> 1.2f;
            case MEDIUM -> 1.0f;
            case LOW    -> 0.8f;
        };
        return similarity * decayScore * w;
    }

    public boolean isEvicted() { return decayScore < 0.1f; }

    /** Apply one day of Ebbinghaus decay */
    public void applyDailyDecay() {
        float rate = switch (importance) {
            case HIGH   -> 0.995f;
            case MEDIUM -> 0.985f;
            case LOW    -> 0.970f;
        };
        this.decayScore = this.decayScore * rate;
    }

    public String     getId()           { return id; }
    public String     getSquadId()      { return squadId; }
    public String     getAgentId()      { return agentId; }
    public String     getSessionId()    { return sessionId; }
    public MemoryType getType()         { return type; }
    public String     getContent()      { return content; }
    public float[]    getEmbedding()    { return embedding; }
    public Importance getImportance()   { return importance; }
    public float      getDecayScore()   { return decayScore; }
    public int        getAccessCount()  { return accessCount; }
    public Instant    getLastAccessed() { return lastAccessed; }
    public String[]   getTags()         { return tags; }
    public Instant    getCreatedAt()    { return createdAt; }

    public void setEmbedding(float[] e) { this.embedding  = e; }
    public void setDecayScore(float s)  { this.decayScore = s; }

    @Override
    public String toString() {
        return "MemoryRecord{id='" + id.substring(0,8) + "', type=" + type
               + ", decay=" + String.format("%.3f", decayScore)
               + ", content='" + content.substring(0, Math.min(60, content.length())) + "'}";
    }
}
