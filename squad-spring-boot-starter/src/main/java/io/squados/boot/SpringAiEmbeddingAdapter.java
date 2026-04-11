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
        java.util.List<Double> embedding = embeddingModel.embed(text);
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }

    @Override
    public int dimensions() {
        return embeddingModel.dimensions();
    }
}
