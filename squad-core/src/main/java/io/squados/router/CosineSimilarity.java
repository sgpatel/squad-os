package io.squados.router;

/**
 * Cosine similarity utility for float embedding vectors.
 *
 * For L2-normalised vectors (as produced by {@link io.squados.memory.retrieval.MockEmbeddingPort}
 * and standard embedding models), cosine similarity equals the dot product.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {}

    /**
     * Compute cosine similarity between two float vectors.
     * Returns a value in [-1.0, 1.0]; higher is more similar.
     *
     * @throws IllegalArgumentException if vectors have different dimensions or are empty.
     */
    public static float compute(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                "Vector dimension mismatch: " + a.length + " vs " + b.length);
        }
        if (a.length == 0) throw new IllegalArgumentException("Vectors must not be empty.");

        double dot  = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA == 0.0 || normB == 0.0) return 0.0f;
        return (float) Math.max(-1.0, Math.min(1.0, dot / (normA * normB)));
    }
}
