package com.example.review;

import com.example.review.adapters.SpringAiLlmAdapter;
import io.squados.annotation.AgentRole;
import io.squados.annotation.SquadApplication;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.execution.SquadResult;
import io.squados.execution.SquadTask;
import io.squados.llm.LlmPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Scanner;

/**
 * SquadOS Code Review Squad — v1.3
 *
 * Paste a code snippet or PR diff.
 * Three specialist AI agents review it simultaneously:
 *
 *   SecurityReviewer (ANALYST)    — finds vulnerabilities
 *   QualityReviewer  (CRITIC)     — spots code smells and design issues
 *   SuggestionReviewer (EXECUTOR) — gives concrete fixes with code snippets
 *
 * All three run in parallel. Review done in ~10 seconds.
 *
 * Run:
 *   ollama serve
 *   ollama pull llama3.2
 *   mvn spring-boot:run
 */
@SpringBootApplication
@SquadApplication
public class CodeReviewApp {

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(CodeReviewApp.class, args);
    }

    @Bean
    public LlmPort llmPort(ChatClient.Builder builder) {
        return new SpringAiLlmAdapter(builder);
    }

    @Bean
    public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(CodeReviewApp.class, llmPort);
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx) {
        return args -> {
            printBanner();

            // ── Get code to review ────────────────────────────────
            Scanner scanner = new Scanner(System.in);
            System.out.println("Paste the code to review (press Enter twice when done):\n");

            StringBuilder code = new StringBuilder();
            int emptyLines = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isBlank()) { if (++emptyLines >= 2) break; }
                else { emptyLines = 0; code.append(line).append("\n"); }
            }

            String snippet = code.toString().trim();
            if (snippet.isEmpty()) { System.out.println("No code provided."); return; }

            System.out.println("\n⏳ Running 3 reviewers in parallel...\n");

            // ── Parallel review — all 3 agents simultaneously ─────
            SquadResult result = ctx.execute(
                SquadTask.of("Review this code:\n\n" + snippet)
                    .assignTo(AgentRole.ANALYST, AgentRole.CRITIC, AgentRole.EXECUTOR)
                    .withLabel("Code Review")
                    .withTimeout(120_000)
            );

            System.out.printf("✓ Review complete in %dms (%.1fx faster than sequential)%n%n",
                result.wallClockMs(), result.speedupRatio());

            // ── Print results ─────────────────────────────────────
            printSection("SECURITY REVIEW", "SecurityReviewer",
                result.get(AgentRole.ANALYST));
            printSection("CODE QUALITY", "QualityReviewer",
                result.get(AgentRole.CRITIC));
            printSection("SUGGESTED FIXES", "SuggestionReviewer",
                result.get(AgentRole.EXECUTOR));

            // ── Overall verdict ───────────────────────────────────
            printOverallVerdict(result);
        };
    }

    private void printBanner() {
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  SquadOS — Multi-Agent AI Framework for Java");
        System.out.println("  Code Review Squad · 3 specialist agents in parallel");
        System.out.println("=======================================================");
        System.out.println();
    }

    private void printSection(String title, String agent,
                               io.squados.agent.AgentResponse response) {
        System.out.println("\n" + "─".repeat(54));
        System.out.printf("  %s  [%s]%n", title, agent);
        System.out.println("─".repeat(54));
        if (response == null || !response.hasContent()) {
            System.out.println("  (no response)");
            return;
        }
        System.out.println(response.content().strip());
    }

    private void printOverallVerdict(SquadResult result) {
        // Derive overall verdict from security + quality responses
        String security = result.get(AgentRole.ANALYST) != null
            ? result.get(AgentRole.ANALYST).content() : "";
        String quality  = result.get(AgentRole.CRITIC) != null
            ? result.get(AgentRole.CRITIC).content() : "";

        boolean blocked  = security != null && security.contains("BLOCK");
        boolean changes  = (security != null && security.contains("REQUEST CHANGES"))
                        || (quality  != null && quality.contains("REQUEST CHANGES"));

        System.out.println("\n" + "=".repeat(54));
        if (blocked) {
            System.out.println("  OVERALL VERDICT:  BLOCK — critical security issue found");
        } else if (changes) {
            System.out.println("  OVERALL VERDICT:  REQUEST CHANGES before merging");
        } else {
            System.out.println("  OVERALL VERDICT:  APPROVE — no blocking issues found");
        }
        System.out.println("=".repeat(54) + "\n");
    }
}
