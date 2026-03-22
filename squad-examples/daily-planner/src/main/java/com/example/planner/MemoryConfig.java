package com.example.planner;

import io.squados.memory.MemoryManager;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.MemoryStoreFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MemoryConfig {

    @Value("${squados.memory.pgvector.enabled:false}")
    private boolean pgvectorEnabled;

    @Value("${squados.memory.real-embeddings:false}")
    private boolean realEmbeddings;

    @Value("${squados.memory.embedding.dimensions:64}")
    private int dimensions;

    /** Mock — keyword similarity, no model needed (default) */
    @Bean
    @ConditionalOnProperty(name = "squados.memory.real-embeddings",
                           havingValue = "false", matchIfMissing = true)
    public EmbeddingPort mockEmbeddingPort() {
        System.out.println("[Memory] Using MockEmbeddingPort (keyword similarity)");
        return new MockEmbeddingPort();
    }

    /** Real semantic embeddings via Ollama nomic-embed-text */
    @Bean
    @ConditionalOnProperty(name = "squados.memory.real-embeddings", havingValue = "true")
    public EmbeddingPort ollamaEmbeddingPort(EmbeddingModel embeddingModel) {
        System.out.println("[Memory] Using Ollama semantic embeddings");
        // Inline adapter — Spring AI lives here in daily-planner, not squad-core
        return new EmbeddingPort() {
            private final int dims = embeddingModel.embed("probe").length;
            @Override public float[] embed(String text) { return embeddingModel.embed(text); }
            @Override public int dimensions() { return dims; }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "squados.memory.pgvector.enabled",
                           havingValue = "false", matchIfMissing = true)
    public MemoryRouter inProcessMemoryRouter() {
        System.out.println("[Memory] In-memory store (resets on restart)");
        return MemoryStoreFactory.inProcess();
    }

    @Bean
    @ConditionalOnProperty(name = "squados.memory.pgvector.enabled", havingValue = "true")
    public MemoryRouter pgVectorMemoryRouter(DataSource dataSource) {
        int dims = realEmbeddings ? 768 : dimensions;
        System.out.println("[Memory] pgvector store (" + dims + " dims)");
        return MemoryStoreFactory.withPgVector(dataSource, dims);
    }

    @Bean
    public MemoryManager memoryManager(MemoryRouter memoryRouter,
                                       EmbeddingPort embeddingPort) {
        return new MemoryManager(memoryRouter, embeddingPort);
    }
}