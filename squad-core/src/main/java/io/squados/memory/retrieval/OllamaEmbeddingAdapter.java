package io.squados.memory.retrieval;

/**
 * OllamaEmbeddingAdapter has been moved to squad-starter and squad-examples.
 *
 * squad-core has ZERO runtime dependencies by design.
 * Spring AI EmbeddingModel is a Spring AI class — it cannot live in squad-core.
 *
 * Use OllamaEmbeddingAdapter from:
 *   squad-starter/src/main/java/com/example/adapters/SpringAiEmbeddingAdapter.java
 *
 * Or implement EmbeddingPort directly:
 *
 *   public class MyEmbeddingAdapter implements EmbeddingPort {
 *       public float[] embed(String text) { ... }
 *       public int dimensions() { return 768; }
 *   }
 *
 * @deprecated Use SpringAiEmbeddingAdapter in squad-starter instead.
 */
@Deprecated
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private static final String ERROR =
        "OllamaEmbeddingAdapter cannot be used directly from squad-core. " +
        "Use SpringAiEmbeddingAdapter from squad-starter instead.";

    @Override
    public float[] embed(String text) {
        throw new UnsupportedOperationException(ERROR);
    }

    @Override
    public int dimensions() {
        throw new UnsupportedOperationException(ERROR);
    }
}
