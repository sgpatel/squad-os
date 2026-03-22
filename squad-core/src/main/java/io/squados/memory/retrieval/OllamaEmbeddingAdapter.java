package io.squados.memory.retrieval;

/**
 * Stub — Ollama embedding adapter moved to consumer modules.
 *
 * squad-core has ZERO runtime dependencies by design.
 * The real adapter is in squad-starter and squad-examples:
 * {@code com.example.adapters.SpringAiEmbeddingAdapter}
 *
 * To wire real semantic embeddings:
 * <pre>
 * // In your Spring Boot app (squad-starter or squad-examples):
 * {@literal @}Bean
 * public EmbeddingPort embeddingPort(EmbeddingModel embeddingModel) {
 *     return new SpringAiEmbeddingAdapter(embeddingModel);
 * }
 * </pre>
 *
 * Pull model first: {@code ollama pull nomic-embed-text}
 *
 * @deprecated Use SpringAiEmbeddingAdapter from your application module.
 */
@Deprecated
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    @Override
    public float[] embed(String text) {
        throw new UnsupportedOperationException(
            "OllamaEmbeddingAdapter is a stub. " +
            "Use SpringAiEmbeddingAdapter in your application module.");
    }

    @Override
    public int dimensions() {
        throw new UnsupportedOperationException(
            "OllamaEmbeddingAdapter is a stub.");
    }
}
