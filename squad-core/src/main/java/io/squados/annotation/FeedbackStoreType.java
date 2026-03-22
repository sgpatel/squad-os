package io.squados.annotation;
/** Which backend to use for storing feedback examples. */
public enum FeedbackStoreType {
    /** In-memory — resets on restart. Good for testing. */
    IN_PROCESS,
    /** pgvector — semantic similarity search, persists across restarts. */
    PGVECTOR,
    /** Redis — fast retrieval, optional persistence. */
    REDIS
}