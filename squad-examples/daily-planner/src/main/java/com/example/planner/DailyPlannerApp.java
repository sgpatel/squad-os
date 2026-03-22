package com.example.planner;

import com.example.planner.adapters.SpringAiLlmAdapter;
import com.example.planner.plans.CoachAdvice;
import com.example.planner.plans.DayPlan;
import com.example.planner.plans.TimeEstimate;
import io.squados.annotation.AgentRole;
import io.squados.annotation.SquadApplication;
import io.squados.config.SquadConfigBridge;
import io.squados.context.SquadContext;
import io.squados.context.SquadRunner;
import io.squados.exception.SquadPlanException;
import io.squados.llm.LlmPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/**
 * SquadOS Daily Planner — with @SquadPlan typed output.
 *
 * Each agent now returns a typed Java object instead of a raw string:
 *   Planner      -> DayPlan      (doToday, doLater, dropIt, message)
 *   TimeEstimator -> TimeEstimate (estimates, totalMinutes, verdict)
 *   Coach        -> CoachAdvice  (quickWin, watchOut, startWith)
 *
 * Run: mvn spring-boot:run  (or with pgvector: -Dspring.profiles.active=pgvector)
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

            // ── Get brain dump ─────────────────────────────────────
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

            // ── Past patterns from memory ──────────────────────────
            String pastPatterns = memoryService.getPastPatterns(brainDump);
            if (!pastPatterns.isEmpty()) {
                System.out.println("\n[Memory] Found patterns from past sessions:");
                System.out.println(pastPatterns);
            } else {
                System.out.println("\n[Memory] First session — no past patterns yet.\n");
            }

            String enrichedInput = brainDump +
                (pastPatterns.isEmpty() ? "" : "\n" + pastPatterns);

            System.out.println("⏳ Thinking... (3 agents in parallel, typed output via @SquadPlan)\n");
            long start = System.currentTimeMillis();

            // ── Agent 1: Planner → DayPlan (typed) ────────────────
            DayPlan plan = null;
            try {
                plan = ctx.submitTo(AgentRole.STRATEGIST,
                    "Here is everything on my mind today. Please organise it:\n\n"
                    + enrichedInput, DayPlan.class);
            } catch (SquadPlanException e) {
                System.out.println("[Planner] Could not parse structured output: " + e.getMessage());
            }

            // ── Agent 2: TimeEstimator → TimeEstimate (typed) ─────
            TimeEstimate time = null;
            try {
                time = ctx.submitTo(AgentRole.ANALYST,
                    "Please estimate time for these tasks:\n\n" + brainDump,
                    TimeEstimate.class);
            } catch (SquadPlanException e) {
                System.out.println("[TimeEstimator] Could not parse structured output: " + e.getMessage());
            }

            // ── Agent 3: Coach → CoachAdvice (typed) ──────────────
            CoachAdvice coach = null;
            try {
                coach = ctx.submitTo(AgentRole.SUPPORT,
                    "Here are my tasks for today. Please coach me:\n\n" + brainDump,
                    CoachAdvice.class);
            } catch (SquadPlanException e) {
                System.out.println("[Coach] Could not parse structured output: " + e.getMessage());
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("✓ Done in %dms%n%n", elapsed);

            // ── Print typed results ────────────────────────────────
            printDayPlan(plan);
            printTimeEstimate(time);
            printCoachAdvice(coach);

            // ── Save to memory ─────────────────────────────────────
            String planText = plan != null ? String.join(", ", plan.doToday) : "";
            memoryService.saveSession(brainDump, planText, LocalDate.now().toString());
            System.out.println("\n[Memory] Session saved. Total memories: "
                + memoryService.totalMemories());

            System.out.println("\n" + "=".repeat(50));
            System.out.println("  Go make it happen!");
            System.out.println("=".repeat(50) + "\n");
        };
    }

    private void printBanner() {
        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  SquadOS — Multi-Agent AI Framework for Java");
        System.out.println("  Daily Planner · @SquadPlan typed output · v2.1");
        System.out.println("=======================================================");
    }

    private void printDayPlan(DayPlan plan) {
        System.out.println("-".repeat(50));
        System.out.println("  YOUR PLAN FOR TODAY");
        System.out.println("-".repeat(50));
        if (plan == null) { System.out.println("  (unavailable)"); return; }

        System.out.println("\nDO TODAY:");
        printList(plan.doToday);

        if (plan.doLater != null && !plan.doLater.isEmpty()) {
            System.out.println("\nDO LATER:");
            printList(plan.doLater);
        }
        if (plan.dropIt != null && !plan.dropIt.isEmpty()) {
            System.out.println("\nDROP IT:");
            printList(plan.dropIt);
        }
        if (plan.message != null)
            System.out.println("\n" + plan.message);
    }

    private void printTimeEstimate(TimeEstimate time) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println("  TIME REALITY CHECK");
        System.out.println("-".repeat(50));
        if (time == null) { System.out.println("  (unavailable)"); return; }

        if (time.estimates != null) time.estimates.forEach(e -> System.out.println("  " + e));
        System.out.println("\n  TOTAL: " + time.totalMinutes + " minutes");
        System.out.println("  VERDICT: " + time.verdict);
    }

    private void printCoachAdvice(CoachAdvice coach) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println("  YOUR COACH SAYS");
        System.out.println("-".repeat(50));
        if (coach == null) { System.out.println("  (unavailable)"); return; }

        System.out.println("\nQUICK WIN: " + coach.quickWin);
        System.out.println("WATCH OUT: " + coach.watchOut);
        System.out.println("START WITH: " + coach.startWith);
    }

    private void printList(List<String> items) {
        if (items == null) return;
        for (int i = 0; i < items.size(); i++)
            System.out.println("  " + (i+1) + ". " + items.get(i));
    }
}
