package com.example.demo;

import io.squados.memory.retrieval.EmbeddingPort;
import io.squados.memory.retrieval.MockEmbeddingPort;
import io.squados.memory.store.InProcessMemoryStore;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * SquadOS Embedding Demo — Mock vs Real semantic similarity.
 *
 * Shows exactly why real embeddings make memory smarter.
 *
 * Run:
 *   ollama pull nomic-embed-text
 *   mvn spring-boot:run
 */
@SpringBootApplication
public class EmbeddingDemoApp {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingDemoApp.class, args);
    }

    @Bean
    public ApplicationRunner runner(EmbeddingModel embeddingModel) {
        return args -> {
            System.out.println();
            System.out.println("=======================================================");
            System.out.println("  SquadOS — Mock vs Real Embedding Comparison");
            System.out.println("=======================================================");

            EmbeddingPort mock = new MockEmbeddingPort();
            EmbeddingPort real = new EmbeddingPort() {
                @Override public float[] embed(String text) {
                    return embeddingModel.embed(text);
                }
                @Override public int dimensions() {
                    return embeddingModel.embed("probe").length;
                }
            };

            System.out.printf("%nEmbedding model: nomic-embed-text (%d dims)%n",
                real.dimensions());

            String[][] pairs = {
                {"fix the login bug",       "auth service is broken"},
                {"buy groceries",           "auth service is broken"},
                {"prepare slides friday",   "presentation for tomorrow meeting"},
                {"call mum",                "phone my mother"},
                {"learn kubernetes",        "study docker containers"},
                {"buy groceries",           "call mum"},
            };

            System.out.println();
            System.out.printf("%-42s  %-8s  %-8s  %s%n",
                "Pair", "Mock sim", "Real sim", "Verdict");
            System.out.println("-".repeat(80));

            for (String[] pair : pairs) {
                float mockSim = cosineSim(mock.embed(pair[0]), mock.embed(pair[1]));
                float realSim = cosineSim(real.embed(pair[0]), real.embed(pair[1]));

                String label = pair[0].substring(0, Math.min(18, pair[0].length()))
                    + " / " + pair[1].substring(0, Math.min(18, pair[1].length()));

                String verdict;
                if (realSim > 0.7f && mockSim < 0.3f)
                    verdict = "REAL WINS - semantic match keyword missed";
                else if (realSim < 0.3f && mockSim < 0.3f)
                    verdict = "Both correctly identify as unrelated";
                else if (realSim > 0.7f && mockSim > 0.7f)
                    verdict = "Both correctly identify as similar";
                else
                    verdict = String.format("realSim=%.2f mockSim=%.2f", realSim, mockSim);

                System.out.printf("%-42s  %-8.3f  %-8.3f  %s%n",
                    label, mockSim, realSim, verdict);
            }

            System.out.println();
            System.out.println("KEY INSIGHT:");
            System.out.println("  'fix the login bug' and 'auth service is broken' mean");
            System.out.println("  the same thing. Real embeddings know this. Mock does not.");
            System.out.println("  This is why pgvector + nomic-embed-text retrieves the");
            System.out.println("  RIGHT past memories even when the words are different.");
            System.out.println();
        };
    }

    private float cosineSim(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denom = (float)(Math.sqrt(normA) * Math.sqrt(normB));
        return denom == 0 ? 0 : dot / denom;
    }
}
