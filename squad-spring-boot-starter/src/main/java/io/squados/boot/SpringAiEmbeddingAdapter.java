package io.squados.boot;

import io.squados.memory.retrieval.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Bridges Spring AI {@link EmbeddingModel} to SquadOS {@link EmbeddingPort}.
 *
 * Used by MemoryManager for semantic memory retrieval when
 * squad.memory.store=pgvector or squad.memory.store=redis.
 *
 * Only imported in squad-spring-boot-starter — squad-core has zero Spring AI deps.
 */
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[0];
        // Spring AI 1.0.0 GA: embed(String) returns float[] directly
        return embeddingModel.embed(text);
    }

    @Override
    public int dimensions() {
        return embeddingModel.dimensions();
    }
}
