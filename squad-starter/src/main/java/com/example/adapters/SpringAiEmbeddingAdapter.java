package com.example.adapters;

import io.squados.memory.retrieval.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Production EmbeddingPort backed by Spring AI EmbeddingModel.
 *
 * Works with any Spring AI embedding provider:
 *   - Ollama nomic-embed-text (local, no API key)   recommended default
 *   - OpenAI text-embedding-3-small (API key needed)
 *
 * Pull the model first:
 *   ollama pull nomic-embed-text
 *
 * Configure in application.properties:
 *   spring.ai.ollama.embedding.options.model=nomic-embed-text
 *
 * Why this matters vs MockEmbeddingPort:
 *   Mock: "fix login bug" vs "auth service broken" = LOW similarity
 *   Real: "fix login bug" vs "auth service broken" = HIGH similarity (same concept)
 */
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;
    private final int            dims;

    public SpringAiEmbeddingAdapter(EmbeddingModel model) {
        this.model = model;
        float[] probe = model.embed("probe");
        this.dims = probe.length;
        System.out.printf("[SquadOS] EmbeddingPort: %s (%d dims)%n",
            model.getClass().getSimpleName(), this.dims);
    }

    @Override
    public float[] embed(String text) {
        return model.embed(text);
    }

    @Override
    public int dimensions() {
        return dims;
    }
}
