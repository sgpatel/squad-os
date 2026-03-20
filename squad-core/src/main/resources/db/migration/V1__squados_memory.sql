-- SquadOS Memory Layer — pgvector schema
-- Run once against your PostgreSQL database.
-- Requires: PostgreSQL 14+ with pgvector extension

-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Main memory table
CREATE TABLE IF NOT EXISTS squad_memories (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    squad_id      VARCHAR(64)  NOT NULL,
    agent_id      VARCHAR(64),                    -- NULL = squad-scoped
    session_id    VARCHAR(64)  NOT NULL,
    memory_type   VARCHAR(20)  NOT NULL,          -- WORKING/SEMANTIC/PROCEDURAL/EPISODIC
    content       TEXT         NOT NULL,
    embedding     vector(64),                     -- 64 for dev; 1536 for OpenAI prod
    importance    SMALLINT     NOT NULL DEFAULT 2, -- 1=LOW 2=MEDIUM 3=HIGH
    decay_score   FLOAT        NOT NULL DEFAULT 1.0,
    access_count  INT          NOT NULL DEFAULT 0,
    last_accessed TIMESTAMPTZ,
    tags          TEXT[]       NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- HNSW index for fast approximate nearest-neighbour search
-- ef_construction=128 balances build speed vs recall quality
CREATE INDEX IF NOT EXISTS squad_memories_embedding_idx
    ON squad_memories USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);

-- Composite index for session + type queries
CREATE INDEX IF NOT EXISTS squad_memories_session_idx
    ON squad_memories (squad_id, memory_type, decay_score DESC);

-- Index for agent-scoped lookups
CREATE INDEX IF NOT EXISTS squad_memories_agent_idx
    ON squad_memories (squad_id, agent_id, memory_type);

-- Comments
COMMENT ON TABLE  squad_memories              IS 'SquadOS multi-agent episodic memory store';
COMMENT ON COLUMN squad_memories.decay_score  IS 'Ebbinghaus decay: 1.0=fresh, <0.1=evicted';
COMMENT ON COLUMN squad_memories.embedding    IS 'Float vector from embedding model';
COMMENT ON COLUMN squad_memories.importance   IS '1=LOW(rate 0.97) 2=MED(0.985) 3=HIGH(0.995)';
