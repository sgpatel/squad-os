package com.example.planner;

import io.squados.memory.MemoryManager;
import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MemoryRouter;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.MemoryStoreFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Wires memory layer based on squados.memory.pgvector.enabled.
 *
 * MODE 1 — in-memory (default, no infrastructure):
 *   mvn spring-boot:run
 *   Memories reset on restart. Good for trying out the planner.
 *
 * MODE 2 — pgvector persistent:
 *   docker-compose up -d
 *   mvn spring-boot:run -Dspring.profiles.active=pgvector
 *   Memories persist across restarts. Oracle learns your patterns.
 */
@Configuration
public class MemoryConfig {

    @Value("${squados.memory.pgvector.enabled:false}")
    private boolean pgvectorEnabled;

    @Value("${squados.memory.embedding.dimensions:64}")
    private int dimensions;

    @Bean
    public EmbeddingPort embeddingPort() {
        return new MockEmbeddingPort();
    }

    /**
     * In-memory router — no database needed.
     * Active when squados.memory.pgvector.enabled=false (default).
     */
    @Bean
    @ConditionalOnProperty(name = "squados.memory.pgvector.enabled", havingValue = "false", matchIfMissing = true)
    public MemoryRouter inProcessMemoryRouter() {
        System.out.println("[Memory] In-memory mode — memories reset on restart.");
        System.out.println("[Memory] To persist, run: -Dspring.profiles.active=pgvector");
        return MemoryStoreFactory.inProcess();
    }

    /**
     * pgvector router — persists to PostgreSQL.
     * Active when squados.memory.pgvector.enabled=true (pgvector profile).
     */
    @Bean
    @ConditionalOnProperty(name = "squados.memory.pgvector.enabled", havingValue = "true")
    public MemoryRouter pgVectorMemoryRouter(DataSource dataSource) {
        System.out.println("[Memory] pgvector mode — memories persist to PostgreSQL.");
        return MemoryStoreFactory.withPgVector(dataSource, dimensions);
    }

    @Bean
    public MemoryManager memoryManager(MemoryRouter memoryRouter, EmbeddingPort embeddingPort) {
        return new MemoryManager(memoryRouter, embeddingPort);
    }
}