package com.example.adapters;

import io.squados.memory.retrieval.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Production EmbeddingPort backed by Spring AI EmbeddingModel.
 *
 * Supports any Spring AI embedding provider:
 *   - Ollama nomic-embed-text  (768 dims, local, no API key)
 *   - Ollama mxbai-embed-large (1024 dims, local, higher quality)
 *   - OpenAI text-embedding-3-small (1536 dims, needs API key)
 *
 * Setup:
 *   ollama pull nomic-embed-text
 *
 * Configure in application.properties:
 *   spring.ai.ollama.embedding.options.model=nomic-embed-text
 *
 * Why real embeddings matter over MockEmbeddingPort:
 *   Mock:  "fix login bug"  vs "auth service broken"  = 0.02 similarity (keyword miss)
 *   Real:  "fix login bug"  vs "auth service broken"  = 0.87 similarity (semantic match)
 *
 * This is the ONLY class in squad-starter that imports Spring AI embedding classes.
 */
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;
    private final int            dims;

    public SpringAiEmbeddingAdapter(EmbeddingModel model) {
        this.model = model;
        // Probe dimensions once at startup
        float[] probe = model.embed("warmup");
        this.dims = probe.length;
        System.out.printf("[SquadOS] Real embeddings active: %s (%d dims)%n",
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
