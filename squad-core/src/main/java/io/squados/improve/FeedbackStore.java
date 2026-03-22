package io.squados.improve;

import java.util.List;

/**
 * Storage for feedback examples used by @Improve.
 *
 * Implementations:
 *   InProcessFeedbackStore — in-memory, for testing
 *   PgVectorFeedbackStore  — semantic similarity search (coming in v3.0)
 */
public interface FeedbackStore {

    /** Save a new feedback example. */
    void save(FeedbackExample example);

    /**
     * Find the top-K most similar GOOD examples for a given input.
     * Similarity is measured by keyword overlap (in-process)
     * or cosine similarity (pgvector).
     */
    List<FeedbackExample> findSimilarGood(String methodLabel, String input, int topK);

    /**
     * Find the top-K most similar BAD examples for a given input.
     * Used when includeNegativeExamples=true.
     */
    List<FeedbackExample> findSimilarBad(String methodLabel, String input, int topK);

    /** All examples for a method label. */
    List<FeedbackExample> findAll(String methodLabel);

    /** Count of stored examples for a method label. */
    default int count(String methodLabel) { return findAll(methodLabel).size(); }

    /** Mark an example as GOOD by ID. */
    void markGood(String exampleId, String note);

    /** Mark an example as BAD by ID. */
    void markBad(String exampleId, String note);
}