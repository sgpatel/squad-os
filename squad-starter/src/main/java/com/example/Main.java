package com.example;

import com.example.adapters.SpringAiLlmAdapter;
import com.example.adapters.SpringAiEmbeddingAdapter;
import io.squados.annotation.SquadApplication;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.llm.LlmPort;
import io.squados.memory.retrieval.EmbeddingPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * SquadOS — running on local Ollama llama3.2.
 *
 * Prerequisites:
 *   1. Install Ollama: https://ollama.ai
 *   2. ollama serve
 *   3. ollama pull llama3.2
 *   4. ollama pull nomic-embed-text
 *
 * Run: mvn spring-boot:run  (no API key needed)
 */
@SpringBootApplication
@SquadApplication
public class Main {

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(Main.class, args);
    }

    // ── Spring AI wiring ──────────────────────────────────────────────

    @Bean
    public LlmPort llmPort(ChatClient.Builder builder) {
        return new SpringAiLlmAdapter(builder);
    }

    @Bean
    public EmbeddingPort embeddingPort(EmbeddingModel embeddingModel) {
        return new SpringAiEmbeddingAdapter(embeddingModel);
    }

    @Bean
    public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(Main.class, llmPort);
    }

    // ── Entry point — ApplicationRunner avoids @Autowired cycle ──────

    @Bean
    public ApplicationRunner runner(SquadContext squadContext) {
        return (ApplicationArguments args) -> {
            System.out.println("\n═══════════════════════════════════════");
            System.out.println("  SquadOS — Ollama llama3.2 Live Demo");
            System.out.println("═══════════════════════════════════════");

            var response = squadContext.submit(
                "Plan a tactical assault on the enemy north gate. " +
                "Enemy has 3 defenders — two at the gate, one sniper on the right flank. " +
                "Keep the plan under 100 words."
            );

            System.out.println("\nOracle's plan:");
            System.out.println(response.content());
            System.out.printf("%nLatency: %dms%n", response.latency().toMillis());
        };
    }
}
