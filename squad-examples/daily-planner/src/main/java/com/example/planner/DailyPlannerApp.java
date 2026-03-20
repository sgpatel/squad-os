package com.example.planner;

import com.example.planner.adapters.SpringAiLlmAdapter;
import io.squados.annotation.SquadApplication;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.llm.LlmPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Scanner;

/**
 * SquadOS Daily Planner — a real-world layman example.
 *
 * HOW IT WORKS:
 *   You type everything on your mind (messy, unorganised — that's fine).
 *   Three AI agents process your input in parallel:
 *
 *   🧠 Planner (Strategist) — sorts your tasks: DO TODAY / DO LATER / DROP IT
 *   ⏱  TimeEstimator (Analyst) — adds time estimates, warns if you're overloaded
 *   💪 Coach (Support) — gives you a quick win + tells you exactly where to start
 *
 * RUN:
 *   ollama serve
 *   ollama pull llama3.2
 *   mvn spring-boot:run
 *
 * Then type (or paste) everything that's on your mind when prompted.
 */
@SpringBootApplication
@SquadApplication
public class DailyPlannerApp {

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

            // ── Get the user's brain dump ─────────────────────────
            Scanner scanner = new Scanner(System.in);
            System.out.println("\nWhat's on your mind today? " +
                "(paste everything, press Enter twice when done)\n");

            StringBuilder input = new StringBuilder();
            String line;
            int emptyLines = 0;
            while (scanner.hasNextLine()) {
                line = scanner.nextLine();
                if (line.isBlank()) {
                    emptyLines++;
                    if (emptyLines >= 2) break;
                } else {
                    emptyLines = 0;
                    input.append(line).append("\n");
                }
            }

            String brainDump = input.toString().trim();
            if (brainDump.isEmpty()) {
                System.out.println("Nothing entered. Have a great day!");
                return;
            }

            System.out.println("\n⏳ Thinking...\n");

            // ── Agent 1: Planner — sorts and prioritises ──────────
            var plannerResponse = ctx.submitTo(
                io.squados.annotation.AgentRole.STRATEGIST,
                "Here is everything on my mind today. Please organise it:\n\n" + brainDump
            );

            // ── Agent 2: TimeEstimator — realistic time check ─────
            var timeResponse = ctx.submitTo(
                io.squados.annotation.AgentRole.ANALYST,
                "Please estimate time for these tasks:\n\n" + brainDump
            );

            // ── Agent 3: Coach — momentum and first step ──────────
            var coachResponse = ctx.submitTo(
                io.squados.annotation.AgentRole.SUPPORT,
                "Here are my tasks for today. Please coach me:\n\n" + brainDump
            );

            // ── Print results ─────────────────────────────────────
            printSection("📋 YOUR PLAN FOR TODAY", plannerResponse.content());
            printSection("⏱  TIME REALITY CHECK", timeResponse.content());
            printSection("💪 YOUR COACH SAYS", coachResponse.content());

            System.out.println("\n" + "═".repeat(50));
            System.out.println("  Go make it happen! 🚀");
            System.out.println("═".repeat(50) + "\n");
        };
    }

    private void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       SquadOS Daily Planner                  ║");
        System.out.println("║  3 AI agents to organise your day in 10s     ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }

    private void printSection(String title, String content) {
        System.out.println("\n" + "─".repeat(50));
        System.out.println("  " + title);
        System.out.println("─".repeat(50));
        if (content == null) { System.out.println("(no response)"); return; }
        // Strip internal SquadOS Task ID line if present
        String cleaned = java.util.Arrays.stream(content.split("\n"))
            .filter(l -> !l.strip().startsWith("Task ID:"))
            .collect(java.util.stream.Collectors.joining("\n"))
            .strip();
        System.out.println(cleaned);
    }
}
