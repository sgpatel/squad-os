package io.squados.memory.retrieval;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

/**
 * Production EmbeddingPort backed by LangChain4j EmbeddingModel.
 *
 * This is the ONLY class in SquadOS that imports LangChain4j classes.
 *
 * Supported models (configure in squad.yml or Spring Boot properties):
 *   - OpenAI text-embedding-3-small  (1536 dims, recommended)
 *   - OpenAI text-embedding-3-large  (3072 dims)
 *   - all-MiniLM-L6-v2               (384 dims, local — no API key)
 *   - nomic-embed-text               (768 dims, local via Ollama)
 *
 * Wiring in your Spring Boot app:
 * <pre>
 * {@literal @}Bean
 * public EmbeddingPort embeddingPort(EmbeddingModel embeddingModel) {
 *     return new LangChain4jEmbeddingAdapter(embeddingModel);
 * }
 * </pre>
 *
 * Required pom.xml dependency:
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;dev.langchain4j&lt;/groupId&gt;
 *   &lt;artifactId&gt;langchain4j-open-ai-spring-boot-starter&lt;/artifactId&gt;
 *   &lt;version&gt;1.0.0&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 */
public class LangChain4jEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel model;
    private final int            dimensions;

    public LangChain4jEmbeddingAdapter(EmbeddingModel model) {
        this.model      = model;
        // Probe dimensions with a single embed call at construction time
        this.dimensions = model.embed("probe").content().vector().length;
    }

    @Override
    public float[] embed(String text) {
        Embedding embedding = model.embed(TextSegment.from(text)).content();
        return embedding.vector();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
