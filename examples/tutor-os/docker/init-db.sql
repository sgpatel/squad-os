-- Postgres init script — runs ONCE on a fresh `postgres-data` volume.
-- The pgvector image (pgvector/pgvector:pg16) ships the extension
-- pre-installed; we just enable it for the application database so
-- M1's PgVectorEpisodicStore can CREATE TABLE … vector(N) columns.
--
-- The M3-JDBC mastery store auto-bootstraps its own schema on first
-- use, so this script doesn't need to know about it.
--
-- POSTGRES_DB=tutoros (set via the compose env) → init-db.sql runs
-- inside that database automatically; no need for a CREATE DATABASE.

CREATE EXTENSION IF NOT EXISTS vector;

-- A learner-friendly note that ends up in pg's logs at first boot.
DO $$
BEGIN
  RAISE NOTICE 'pgvector extension enabled — M1 episodic memory + M3 mastery graph ready';
END
$$;
