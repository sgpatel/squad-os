package com.example.planner;

import com.example.planner.adapters.SpringAiLlmAdapter;
import io.squados.annotation.SquadApplication;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.execution.SquadResult;
import io.squados.execution.SquadTask;
import io.squados.llm.LlmPort;
import io.squados.annotation.AgentRole;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDate;
import java.util.Scanner;

/**
 * SquadOS Daily Planner — with persistent pgvector memory.
 *
 * Day 1: agents give general advice.
 * Day 7: agents know you always defer "learn kubernetes".
 *         Oracle stops putting it in DO LATER — puts it in DROP IT automatically.
 *
 * Prerequisites:
 *   1. ollama serve + ollama pull llama3.2
 *   2. docker-compose up -d   (starts PostgreSQL + pgvector)
 *
 * Run: mvn spring-boot:run
 */
@SpringBootApplication
@SquadApplication
public class DailyPlannerApp {

    @Autowired
    private PlannerMemoryService memoryService;

    public static void main(String[] args) {
        SquadConfigBridge.applyToSystemProperties();
        SpringApplication.run(DailyPlannerApp.class, args);
    }

    @Bean
    public LlmPort llmPort(ChatClient.Builder builder) {
        return new SpringAiLlmAdapter(builder);
    }

    @Bean
    public SquadContext squadContext(LlmPort llmPort) {
        return SquadRunner.run(DailyPlannerApp.class, llmPort);
    }

    @Bean
    public ApplicationRunner runner(SquadContext ctx) {
        return args -> {
            printBanner();

            // ── Get brain dump ────────────────────────────────────
            Scanner scanner = new Scanner(System.in);
            System.out.println("\nWhat's on your mind today? " +
                "(paste everything, press Enter twice when done)\n");

            StringBuilder input = new StringBuilder();
            int emptyLines = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isBlank()) { if (++emptyLines >= 2) break; }
                else { emptyLines = 0; input.append(line).append("\n"); }
            }

            String brainDump = input.toString().trim();
            if (brainDump.isEmpty()) { System.out.println("Nothing entered."); return; }

            // ── Retrieve past patterns from pgvector ──────────────
            String pastPatterns = memoryService.getPastPatterns(brainDump);
            if (!pastPatterns.isEmpty()) {
                System.out.println("\n[Memory] Found patterns from past sessions:\n" + pastPatterns);
            } else {
                System.out.println("\n[Memory] First session — no past patterns yet.\n");
            }

            System.out.println("⏳ Thinking... (all 3 agents running in parallel)\n");

            // ── Build enriched input — brain dump + past patterns ─
            // Past patterns are prepended so all 3 agents are aware of your history
            String enrichedInput = brainDump +
                (pastPatterns.isEmpty() ? "" : "\n" + pastPatterns);

            // ── Parallel execution — all 3 agents simultaneously ──
            SquadResult result = ctx.execute(
                SquadTask.of(enrichedInput)
                    .assignTo(AgentRole.STRATEGIST, AgentRole.ANALYST, AgentRole.SUPPORT)
                    .withLabel("Daily Planning")
                    .withTimeout(120_000)
            );

            System.out.printf("✓ Done in %dms (%.1fx faster than sequential)%n%n",
                result.wallClockMs(), result.speedupRatio());

            // ── Print results ─────────────────────────────────────
            String plan = result.get(AgentRole.STRATEGIST) != null
                ? result.get(AgentRole.STRATEGIST).content() : null;

            printSection("YOUR PLAN FOR TODAY", plan);
            printSection("TIME REALITY CHECK",
                result.get(AgentRole.ANALYST) != null
                    ? result.get(AgentRole.ANALYST).content() : null);
            printSection("YOUR COACH SAYS",
                result.get(AgentRole.SUPPORT) != null
                    ? result.get(AgentRole.SUPPORT).content() : null);

            // ── Save to pgvector for next session ─────────────────
            memoryService.saveSession(brainDump, plan, LocalDate.now().toString());
            System.out.println("\n[Memory] Today's plan saved. " +
                "Total memories stored: " + memoryService.totalMemories());

            System.out.println("\n" + "=".repeat(50));
            System.out.println("  Go make it happen!");
            System.out.println("=".repeat(50) + "\n");
        };
    }

    private void printBanner() {
        System.out.println();
        System.out.println("=================================================");
        System.out.println("  SquadOS Daily Planner  (pgvector memory ON)");
        System.out.println("  3 AI agents. Learns your patterns over time.");
        System.out.println("=================================================");
    }

    private void printSection(String title, String content) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println("  " + title);
        System.out.println("-".repeat(50));
        if (content == null) { System.out.println("(no response)"); return; }
        String cleaned = java.util.Arrays.stream(content.split("\n"))
            .filter(l -> !l.strip().startsWith("Task ID:"))
            .collect(java.util.stream.Collectors.joining("\n")).strip();
        System.out.println(cleaned);
    }
}
