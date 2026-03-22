package io.squados.memory.retrieval;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Production EmbeddingPort backed by Spring AI EmbeddingModel.
 *
 * Works with any Spring AI embedding provider:
 *   - Ollama nomic-embed-text (local, no API key)   ← recommended default
 *   - Ollama mxbai-embed-large (local, higher quality)
 *   - OpenAI text-embedding-3-small (1536 dims, API key needed)
 *
 * Pull the model first:
 *   ollama pull nomic-embed-text
 *
 * Configure in application.properties:
 *   spring.ai.ollama.embedding.options.model=nomic-embed-text
 *
 * Why this matters vs MockEmbeddingPort:
 *   Mock: "fix login bug" vs "auth service broken" = LOW similarity (no shared keywords)
 *   Real: "fix login bug" vs "auth service broken" = HIGH similarity (same concept)
 *
 * This is the ONLY class in squad-core that imports Spring AI embedding classes.
 * All other memory code uses EmbeddingPort only.
 */
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;
    private final int            dims;

    public OllamaEmbeddingAdapter(EmbeddingModel model) {
        this.model = model;
        // Probe dimensions once at construction — nomic-embed-text = 768
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
