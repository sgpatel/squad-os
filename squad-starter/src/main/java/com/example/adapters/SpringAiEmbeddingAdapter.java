package com.example.adapters;

import io.squados.memory.retrieval.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * EmbeddingPort backed by Spring AI EmbeddingModel.
 * Works with Ollama, OpenAI, or any other Spring AI embedding provider.
 * No LangChain4j dependency required.
 */
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;

    public SpringAiEmbeddingAdapter(EmbeddingModel model) {
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        // Spring AI embed(String) returns float[] directly
        return model.embed(text);
    }

    @Override
    public int dimensions() {
        return model.dimensions();
    }
}
