package io.squados.memory.retrieval;

/**
 * Stub — LangChain4j adapter moved to consumer modules.
 *
 * squad-core has ZERO runtime dependencies by design.
 * Implement {@link EmbeddingPort} directly in your application module:
 *
 * <pre>
 * public class MyEmbeddingAdapter implements EmbeddingPort {
 *     public float[] embed(String text) { ... }
 *     public int dimensions() { return 768; }
 * }
 * </pre>
 *
 * @deprecated Use a concrete EmbeddingPort implementation in your app module.
 */
@Deprecated
public class LangChain4jEmbeddingAdapter implements EmbeddingPort {

    @Override
    public float[] embed(String text) {
        throw new UnsupportedOperationException(
            "LangChain4jEmbeddingAdapter is a stub. " +
            "Implement EmbeddingPort in your application module.");
    }

    @Override
    public int dimensions() {
        throw new UnsupportedOperationException(
            "LangChain4jEmbeddingAdapter is a stub.");
    }
}
