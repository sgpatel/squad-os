package io.squados.memory.retrieval;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic embedding port for tests and Phase 2 development.
 * Same text always produces the same vector. Similar texts produce similar vectors.
 * Phase 3 replaces this with LangChain4jEmbeddingAdapter.
 */
public class MockEmbeddingPort implements EmbeddingPort {

    private static final int DIM = 64;
    private final Map<String, float[]> cache = new HashMap<>();

    @Override public float[] embed(String text) {
        return cache.computeIfAbsent(text, this::generate);
    }
    @Override public int dimensions() { return DIM; }

    private float[] generate(String text) {
        float[] vec = new float[DIM];
        Random base = new Random(text.toLowerCase().hashCode());
        for (int i = 0; i < DIM; i++) vec[i] = (float) base.nextGaussian();
        for (String word : text.toLowerCase().split("\\s+")) {
            Random wr = new Random(word.hashCode());
            vec[Math.abs(wr.nextInt()) % DIM] += 2.0f;
            vec[Math.abs(wr.nextInt()) % DIM] += 1.5f;
        }
        return l2Normalize(vec);
    }

    private static float[] l2Normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float)(v[i] / norm);
        return out;
    }
}
